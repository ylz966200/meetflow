package com.ylz.futuremade.security;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.ylz.futuremade.meeting.service.OutlineGenerator;
import com.ylz.futuremade.meeting.service.MinutesGenerator;
import com.ylz.futuremade.meeting.service.TranscriptOrganizer;
import com.ylz.futuremade.meeting.service.ChunkExtractionAgent;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "meetflow.jwt.secret-base64=dGVzdC1rZXktZm9yLW1lZXRmbG93LW9ubHktMzItYnl0ZXMtbWluaW11bQ==",
        "meetflow.speech.api-key=",
        "meetflow.speech.poll.enabled=false"
})
@AutoConfigureMockMvc
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.ylz.futuremade.meeting.mapper.TranscriptionTaskMapper transcriptionTasks;
    @Autowired com.ylz.futuremade.meeting.mapper.RecordingMapper recordingMapper;
    @Autowired com.ylz.futuremade.meeting.service.TranscriptionService transcriptionService;
    @Autowired com.ylz.futuremade.prompt.mapper.PromptMapper promptMapper;
    @Autowired com.ylz.futuremade.prompt.service.PromptService promptService;
    @Autowired com.ylz.futuremade.evaluation.mapper.KnowledgeEvaluationRunMapper knowledgeEvaluationRuns;

    @Test void knowledgeEvaluationRecordRetainsAnswerAndAllowsOneReviewRetry() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_knowledge_evaluation_run (id CHAR(36) PRIMARY KEY,dataset_version VARCHAR(64),case_id VARCHAR(100),actor_id BIGINT,meeting_ids VARCHAR(1024),status SMALLINT,error_stage VARCHAR(32),sample_json CLOB,result_json CLOB,source_checks_json CLOB,request_key CHAR(36),review CLOB,judge_call_id BIGINT,manual_review_status SMALLINT DEFAULT 0,manual_review_note VARCHAR(1000),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("DELETE FROM mf_knowledge_evaluation_run");
        var run = new com.ylz.futuremade.evaluation.model.entity.KnowledgeEvaluationRun();
        run.setId("11111111-1111-4111-8111-111111111111"); run.setActorId(1L); run.setMeetingIds("meeting");
        run.setDatasetVersion("1"); run.setCaseId("case"); run.setStatus(3); run.setSampleJson("{}");
        knowledgeEvaluationRuns.insert(run);
        run.setResultJson("{\"answer\":\"结果\"}"); run.setSourceChecksJson("[]");
        knowledgeEvaluationRuns.saveResult(run);
        run.setStatus(5); run.setErrorStage("JUDGE"); knowledgeEvaluationRuns.finish(run);
        org.junit.jupiter.api.Assertions.assertEquals(1, knowledgeEvaluationRuns.claimReviewRetry(run.getId(), 1));
        org.junit.jupiter.api.Assertions.assertEquals(0, knowledgeEvaluationRuns.claimReviewRetry(run.getId(), 1));
        var stored = knowledgeEvaluationRuns.find(run.getId(), 1);
        org.junit.jupiter.api.Assertions.assertTrue(stored.getResultJson().contains("结果"));
        org.junit.jupiter.api.Assertions.assertEquals(0, knowledgeEvaluationRuns.review(run.getId(), 1, 1, "通过"));
        run.setStatus(4); run.setErrorStage(null); knowledgeEvaluationRuns.finish(run);
        org.junit.jupiter.api.Assertions.assertEquals(1, knowledgeEvaluationRuns.review(run.getId(), 1, 1, "通过"));
        org.junit.jupiter.api.Assertions.assertEquals(1, knowledgeEvaluationRuns.find(run.getId(), 1).getManualReviewStatus());
        org.junit.jupiter.api.Assertions.assertNull(knowledgeEvaluationRuns.find(run.getId(), 2));
    }

    /** 使用真实网关与数据库，仅替换外部模型传输。 */
    private void modelRequest(com.ylz.futuremade.assistant.model.dto.AiCallContext context, boolean fail) {
        var transport = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatModel.class);
        var stub = when(transport.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)));
        if (fail) stub.thenThrow(new IllegalStateException("模拟模型失败"));
        else stub.thenReturn(new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(
                new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("测试结果")))));
        var advisor = new com.ylz.futuremade.assistant.service.AiCallAdvisor(promptService, new ObjectMapper());
        new com.ylz.futuremade.assistant.service.AiModelGateway(java.util.Map.of("test", transport), "test", advisor)
                .call(context, "测试输入");
    }

    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @MockBean OutlineGenerator outlineGenerator;
    @MockBean MinutesGenerator minutesGenerator;
    @MockBean TranscriptOrganizer transcriptOrganizer;
    @MockBean ChunkExtractionAgent chunkExtractionAgent;
    @MockBean com.ylz.futuremade.meeting.service.TranscriptOutlineGenerator transcriptOutlineGenerator;

    @BeforeEach void prepareDatabase() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100) UNIQUE, password_hash VARCHAR(100), enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(50) UNIQUE, name VARCHAR(100), enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_user_role (user_id BIGINT, role_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_meeting (id CHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, meeting_time TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_meeting_member (meeting_id CHAR(36), user_id BIGINT, member_role VARCHAR(16), PRIMARY KEY(meeting_id, user_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_transcript_version (meeting_id CHAR(36), version INT, content CLOB, ai_draft_id CHAR(36), created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY(meeting_id, version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_transcript_segment (id BIGINT AUTO_INCREMENT PRIMARY KEY, meeting_id CHAR(36), transcript_version INT, sequence_no INT, content CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(meeting_id,transcript_version,sequence_no))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_transcript_ai_draft (id CHAR(36) PRIMARY KEY, meeting_id CHAR(36), actor_id BIGINT, expected_version INT, request_hash CHAR(64), sources_json CLOB, status SMALLINT, ai_call_id BIGINT, output_content CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_chunk_extraction (id CHAR(36) PRIMARY KEY,meeting_id CHAR(36),task_id CHAR(36),chunk_index INT,speaker_review_version INT,actor_id BIGINT,status SMALLINT,ai_call_id BIGINT,result_json CLOB,chunk_fingerprint VARCHAR(64),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_meeting_member_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, meeting_id CHAR(36), actor_id BIGINT, target_id BIGINT, action VARCHAR(20), old_role VARCHAR(16), new_role VARCHAR(16))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_outline_version (meeting_id CHAR(36), version INT, transcript_version INT, status SMALLINT, items_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY(meeting_id,version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_outline_source (meeting_id CHAR(36),outline_version INT,source_json CLOB,PRIMARY KEY(meeting_id,outline_version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_minutes_version (meeting_id CHAR(36), version INT, outline_version INT, transcript_version INT, status SMALLINT, content CLOB, structured_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(meeting_id,version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_ai_generation_task(id CHAR(36) PRIMARY KEY,request_key CHAR(36) UNIQUE,meeting_id CHAR(36),task_type VARCHAR(40),status SMALLINT,actor_id BIGINT,transcript_version INT,outline_version INT,expected_result_version INT,prompt_version_id BIGINT,model_name VARCHAR(128),config_json CLOB,result_version INT,error_code VARCHAR(64),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,started_at TIMESTAMP,finished_at TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_ai_generation_batch(id CHAR(36) PRIMARY KEY,task_id CHAR(36),batch_no VARCHAR(128),parent_batch_id CHAR(36),status SMALLINT,input_json CLOB,input_hash CHAR(64),output_json CLOB,ai_call_id BIGINT,attempt_count INT DEFAULT 0,error_code VARCHAR(64),lease_owner VARCHAR(128),lease_until TIMESTAMP,attempt_token CHAR(36),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("DELETE FROM mf_ai_generation_batch"); jdbc.update("DELETE FROM mf_ai_generation_task");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_transcription_task (id CHAR(36) PRIMARY KEY, meeting_id CHAR(36), recording_id CHAR(36), created_by BIGINT, status SMALLINT, segments_json CLOB, raw_response CLOB, speaker_review_json CLOB, speaker_review_version INT DEFAULT 0 NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, next_poll_at TIMESTAMP, lease_token CHAR(36), lease_until TIMESTAMP, poll_count INT DEFAULT 0 NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_recording (id CHAR(36) PRIMARY KEY, meeting_id CHAR(36), object_key VARCHAR(255), original_name VARCHAR(255), file_size BIGINT, format VARCHAR(8), transcription_task_id CHAR(36), status SMALLINT, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_generation_request (request_key CHAR(36) PRIMARY KEY, scenario VARCHAR(32), meeting_id CHAR(36), actor_id BIGINT, source_version INT, expected_version INT, status SMALLINT, result_version INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_prompt_scenario (code VARCHAR(64) PRIMARY KEY, name VARCHAR(100), active_version_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_prompt_version (id BIGINT AUTO_INCREMENT PRIMARY KEY, scenario VARCHAR(64), version INT, content CLOB, change_note VARCHAR(500), created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(scenario,version))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_prompt_release_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, scenario VARCHAR(64), old_version_id BIGINT, new_version_id BIGINT, actor_id BIGINT, reason VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_ai_call (id BIGINT AUTO_INCREMENT PRIMARY KEY, scenario VARCHAR(64), prompt_version_id BIGINT, meeting_id CHAR(36), meeting_ids VARCHAR(1024), transcript_version INT, actor_id BIGINT, status SMALLINT, duration_ms BIGINT, error_category VARCHAR(64), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, request_key CHAR(36), outline_version INT, provider VARCHAR(32), model_name VARCHAR(128), provider_request_id VARCHAR(128), input_tokens INT, output_tokens INT, cached_tokens INT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mf_ai_call_content (call_id BIGINT PRIMARY KEY, system_content CLOB NOT NULL, user_content CLOB NOT NULL, output_content CLOB, tool_trace CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("DELETE FROM mf_ai_call_content"); jdbc.update("DELETE FROM mf_ai_call"); jdbc.update("DELETE FROM mf_prompt_release_audit"); jdbc.update("DELETE FROM mf_prompt_version"); jdbc.update("DELETE FROM mf_prompt_scenario");
        jdbc.update("INSERT INTO mf_prompt_scenario VALUES ('MEETING_OUTLINE','会议大纲生成',1)");
        jdbc.update("INSERT INTO mf_prompt_version(id,scenario,version,content,change_note) VALUES(1,'MEETING_OUTLINE',1,'测试提示词','初始化')");
        jdbc.update("INSERT INTO mf_prompt_scenario VALUES ('MEETING_MINUTES','会议纪要生成',2)");
        jdbc.update("INSERT INTO mf_prompt_version(id,scenario,version,content,change_note) VALUES(2,'MEETING_MINUTES',1,'纪要提示词','初始化')");
        jdbc.update("INSERT INTO mf_prompt_scenario VALUES ('TRANSCRIPT_ORGANIZE','会议文字稿整理',3)");
        jdbc.update("INSERT INTO mf_prompt_version(id,scenario,version,content,change_note) VALUES(3,'TRANSCRIPT_ORGANIZE',1,'整理提示词','初始化')");
        jdbc.update("INSERT INTO mf_prompt_scenario VALUES ('TRANSCRIPT_EXTRACTION','会议分段信息提取',4)");
        jdbc.update("INSERT INTO mf_prompt_version(id,scenario,version,content,change_note) VALUES(4,'TRANSCRIPT_EXTRACTION',1,'提取提示词','初始化')");
        jdbc.update("DELETE FROM mf_transcript_segment");
        jdbc.update("DELETE FROM mf_transcript_ai_draft");
        jdbc.update("DELETE FROM mf_chunk_extraction");
        jdbc.update("DELETE FROM mf_minutes_version");
        jdbc.update("DELETE FROM mf_transcription_task");
        jdbc.update("DELETE FROM mf_recording");
        jdbc.update("DELETE FROM mf_generation_request");
        jdbc.update("DELETE FROM mf_outline_source");
        jdbc.update("DELETE FROM mf_outline_version");
        jdbc.update("DELETE FROM mf_meeting_member_audit");
        jdbc.update("DELETE FROM mf_transcript_version");
        jdbc.update("DELETE FROM mf_meeting_member");
        jdbc.update("DELETE FROM mf_meeting");
        jdbc.update("DELETE FROM mf_user_role"); jdbc.update("DELETE FROM mf_role"); jdbc.update("DELETE FROM mf_user");
        jdbc.update("INSERT INTO mf_user VALUES (1, ?, ?, TRUE)", "test-user", new BCryptPasswordEncoder().encode("test-password"));
        jdbc.update("INSERT INTO mf_role VALUES (1, 'USER', '普通用户', TRUE)");
        jdbc.update("INSERT INTO mf_user_role VALUES (1, 1)");
    }

    @Test void meetingKnowledgeRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/meetings/00000000-0000-0000-0000-000000000001/knowledge/index"))
                .andExpect(status().isUnauthorized());
    }
    @Test void milvusConnectionRequiresAdministrator() throws Exception {
        mvc.perform(get("/api/admin/knowledge/milvus/connection")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/knowledge/milvus/connection").header("Authorization", "Bearer " + login()))
                .andExpect(status().isForbidden());
    }
    @Test void loginAndBearerAuthentication() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/app.css")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"test-user\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        String token = login();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("test-user"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test void completedTranscriptionsAreScopedToMeetingAndEditor() throws Exception {
        String token = login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A'),('meeting-b',2,'会议 B')");
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,created_by,status,segments_json) VALUES ('success-a','meeting-a',1,4,'[]'),('processing','meeting-a',1,6,NULL),('success-b','meeting-b',2,4,'[]')");
        jdbc.update("INSERT INTO mf_recording(id,meeting_id,original_name,transcription_task_id) VALUES ('record-a','meeting-a','会议录音.wav','success-a')");
        jdbc.update("UPDATE mf_transcription_task SET recording_id='record-a' WHERE id='success-a'");
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status,segments_json) VALUES ('success-a2','meeting-a','record-a',1,4,'[]')");
        mvc.perform(get("/api/meetings/meeting-a/transcriptions/completed").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].originalName").value("会议录音.wav"))
                .andExpect(jsonPath("$[1].originalName").value("会议录音.wav"));
        mvc.perform(get("/api/meetings/meeting-b/transcriptions/completed").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/meetings/meeting-a/transcriptions/completed?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test void transcriptionCompletionStoresRawResponseOnlyOnce() {
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,created_by,status) VALUES ('task-raw','meeting-a',1,6)");
        String raw = "{\"result\":{\"vendorExtra\":\"原始内容\"}}";
        org.junit.jupiter.api.Assertions.assertEquals(1, transcriptionTasks.complete("task-raw", 4, "[]", raw));
        org.junit.jupiter.api.Assertions.assertEquals(0, transcriptionTasks.complete("task-raw", 4, "[]", "覆盖"));
        org.junit.jupiter.api.Assertions.assertEquals(raw, jdbc.queryForObject("SELECT raw_response FROM mf_transcription_task WHERE id='task-raw'", String.class));
    }

    @Test void recordingRejectsRepeatWhilePreviousTaskIsActive() {
        jdbc.update("INSERT INTO mf_recording(id,meeting_id,original_name,status) VALUES ('record-a','meeting-a','录音.wav',9)");
        org.junit.jupiter.api.Assertions.assertEquals(1, recordingMapper.claimNewTask("meeting-a","record-a","task-1"));
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status) VALUES ('task-1','meeting-a','record-a',1,6)");
        jdbc.update("UPDATE mf_recording SET status=11 WHERE id='record-a'");
        org.junit.jupiter.api.Assertions.assertEquals(0, recordingMapper.claimNewTask("meeting-a","record-a","task-2"));
        jdbc.update("UPDATE mf_transcription_task SET status=4 WHERE id='task-1'");
        org.junit.jupiter.api.Assertions.assertEquals(1, recordingMapper.claimNewTask("meeting-a","record-a","task-2"));
    }

    @Test void recordingListShowsStatusAndHistoryIsMeetingScoped() throws Exception {
        String token=login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A'),('meeting-b',2,'会议 B')");
        jdbc.update("INSERT INTO mf_recording(id,meeting_id,original_name,status,transcription_task_id,file_size) VALUES ('record-a','meeting-a','录音.wav',11,'task-a',100),('record-b','meeting-b','其他.wav',11,'task-b',100)");
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status) VALUES ('task-a','meeting-a','record-a',1,6),('task-old','meeting-a','record-a',1,4),('task-b','meeting-b','record-b',2,4)");
        mvc.perform(get("/api/meetings/meeting-a/transcriptions/recordings").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].transcriptionStatus").value(6));
        mvc.perform(get("/api/meetings/meeting-a/transcriptions/recordings/record-a/history").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/meetings/meeting-a/transcriptions/recordings/record-b/history").header("Authorization","Bearer "+token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/meetings/meeting-b/transcriptions/recordings").header("Authorization","Bearer "+token))
                .andExpect(status().isNotFound());
    }

    @Test void speakerReviewsValidateMembershipAndPreventStaleWrites() throws Exception {
        String token=login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A'),('meeting-b',2,'会议 B')");
        String segments="[{\"startMillis\":0,\"endMillis\":100,\"speakerLabel\":\"1\",\"text\":\"你好\"},{\"startMillis\":100,\"endMillis\":200,\"speakerLabel\":\"1\",\"text\":\"再见\"}]";
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,created_by,status,segments_json) VALUES ('task-a','meeting-a',1,4,?),('task-b','meeting-b',2,4,?)",segments,segments);
        String path="/api/meetings/meeting-a/transcriptions/task-a/speaker-review";
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.assignments.length()").value(2));
        mvc.perform(get("/api/meetings/meeting-a/transcriptions/task-a/chunks/preview").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].utterances[0].taskId").value("task-a"))
                .andExpect(jsonPath("$[0].utterances[0].segmentIndex").value(0));
        mvc.perform(get("/api/meetings/meeting-b/transcriptions/task-b/chunks/preview").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> transcriptionService.contextTools("meeting-b", "task-b", 1, 1));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> transcriptionService.contextTools("meeting-a", "task-a", 1, 2));
        String body="{\"version\":0,\"assignments\":[{\"memberUsername\":\"test-user\"},{\"externalName\":\"外部来宾\"}]}";
        mvc.perform(put(path).header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.assignments[0].memberUsername").value("test-user")).andExpect(jsonPath("$.assignments[1].externalName").value("外部来宾"));
        mvc.perform(put(path).header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(put(path).header("Authorization","Bearer "+token).contentType("application/json").content("{\"version\":1,\"assignments\":[{\"memberUsername\":\"outsider\"},{}]}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/meetings/meeting-b/transcriptions/task-b/speaker-review").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(segments,jdbc.queryForObject("SELECT segments_json FROM mf_transcription_task WHERE id='task-a'",String.class));
    }

    @Test void assemblesReviewedTasksInOrderAndRejectsDuplicateRecordings() throws Exception {
        String token=login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A'),('meeting-b',2,'会议 B')");
        String segments="[{\"startMillis\":0,\"endMillis\":100,\"speakerLabel\":\"1\",\"text\":\"你好\"}]";
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status,segments_json,speaker_review_json,speaker_review_version) VALUES ('task-a','meeting-a','rec-a',1,4,?,'[{\"memberUsername\":\"test-user\"}]',1),('task-b','meeting-a','rec-b',1,4,?,NULL,0),('task-a2','meeting-a','rec-a',1,4,?,NULL,0),('task-other','meeting-b','rec-c',2,4,?,NULL,0)",segments,segments,segments,segments);
        String path="/api/meetings/meeting-a/transcriptions/assemble";
        mvc.perform(post(path).header("Authorization","Bearer "+token).contentType("application/json").content("{\"taskIds\":[\"task-b\",\"task-a\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].taskId").value("task-b")).andExpect(jsonPath("$.sources[1].speakerReviewVersion").value(1))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("test-user：你好")))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("录音 1 的说话人 1：你好")));
        mvc.perform(post(path).header("Authorization","Bearer "+token).contentType("application/json").content("{\"taskIds\":[\"task-a\",\"task-a2\"]}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization","Bearer "+token).contentType("application/json").content("{\"taskIds\":[\"task-other\"]}")).andExpect(status().isNotFound());
    }

    @Test void organizesIdempotentlyAndLinksSavedVersionToSource() throws Exception {
        String token=login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A')");
        String segments="[{\"startMillis\":0,\"endMillis\":100,\"speakerLabel\":\"1\",\"text\":\"你好\"}]";
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status,segments_json) VALUES ('task-a','meeting-a','rec-a',1,4,?)",segments);
        when(transcriptOrganizer.generate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> { modelRequest(inv.getArgument(1), false); return "整理后的内容"; });
        String key="11111111-1111-4111-8111-111111111111", body="{\"taskIds\":[\"task-a\"],\"expectedVersion\":0}";
        String route="/api/meetings/meeting-a/transcriptions/organize";
        mvc.perform(post(route).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.draftId").value(key)).andExpect(jsonPath("$.sources[0].taskId").value("task-a"));
        mvc.perform(post(route).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(post(route).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content("{\"taskIds\":[\"other\"],\"expectedVersion\":0}")).andExpect(status().isConflict());
        org.mockito.Mockito.verify(transcriptOrganizer,org.mockito.Mockito.times(1)).generate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/meetings/meeting-a/transcript").header("Authorization","Bearer "+token).contentType("application/json").content("{\"content\":\"人工审核后的内容\",\"expectedVersion\":0,\"aiDraftId\":\""+key+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/meetings/meeting-a/transcript/source?version=1").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.draftId").value(key)).andExpect(jsonPath("$.sources[0].speakerReviewVersion").value(0)).andExpect(jsonPath("$.aiCallId").isNumber());
        mvc.perform(post("/api/meetings/meeting-a/transcript").header("Authorization","Bearer "+token).contentType("application/json").content("{\"content\":\"重复内容\",\"expectedVersion\":1,\"aiDraftId\":\""+key+"\"}")).andExpect(status().isConflict());
    }

    @Test void chunkExtractionIsIdempotentAndRejectsStaleReview() throws Exception {
        String token=login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A'),('meeting-b',2,'会议 B')");
        String segments="[{\"startMillis\":0,\"endMillis\":100,\"speakerLabel\":\"1\",\"text\":\"你好\"}]";
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,created_by,status,segments_json) VALUES ('task-a','meeting-a',1,4,?)",segments);
        var result=new com.ylz.futuremade.meeting.model.dto.ChunkExtractionView();
        result.setTaskId("task-a");result.setChunkIndex(1);result.setSpeakerReviewVersion(0);result.setItems(java.util.List.of());
        when(chunkExtractionAgent.extract(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(result);
        String key="33333333-3333-4333-8333-333333333333",path="/api/meetings/meeting-a/transcriptions/task-a/chunks/1/extract";
        String body="{\"speakerReviewVersion\":0}";
        mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.taskId").value("task-a"));
        mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isOk());
        org.mockito.Mockito.verify(chunkExtractionAgent,org.mockito.Mockito.times(1)).extract(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key","44444444-4444-4444-8444-444444444444").contentType("application/json").content("{\"speakerReviewVersion\":1}")).andExpect(status().isConflict());
        mvc.perform(post("/api/meetings/meeting-b/transcriptions/task-a/chunks/1/extract").header("Authorization","Bearer "+token).header("Idempotency-Key","55555555-5555-4555-8555-555555555555").contentType("application/json").content(body)).andExpect(status().isNotFound());
    }

    @Test void organizeExplainsLengthLimitBeforeCallingModel() throws Exception {
        String token=login();
        jdbc.update("INSERT INTO mf_meeting(id,owner_id,title) VALUES ('meeting-a',1,'会议 A')");
        String segments="[{\"startMillis\":0,\"endMillis\":100,\"speakerLabel\":\"1\",\"text\":\""+"会".repeat(12001)+"\"}]";
        jdbc.update("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status,segments_json) VALUES ('task-a','meeting-a','rec-a',1,4,?)",segments);
        mvc.perform(post("/api/meetings/meeting-a/transcriptions/organize").header("Authorization","Bearer "+token)
                .header("Idempotency-Key","22222222-2222-4222-8222-222222222222").contentType("application/json")
                .content("{\"taskIds\":[\"task-a\"],\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("超过当前上限 12000")));
        org.mockito.Mockito.verify(transcriptOrganizer,org.mockito.Mockito.never()).generate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
    }

    @Test void disabledOrRecreatedAccountCannotUseOldToken() throws Exception {
        String token = login();
        jdbc.update("UPDATE mf_user SET enabled=FALSE WHERE id=1");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM mf_user_role"); jdbc.update("DELETE FROM mf_user");
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)", "test-user", new BCryptPasswordEncoder().encode("test-password"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void expiredTokenIsRejected() throws Exception {
        var now = java.time.Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("meetflow")
                .subject("test-user").claim("uid", 1).issuedAt(now.minusSeconds(300))
                .expiresAt(now.minusSeconds(120)).build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        var expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test void evaluationDatasetRequiresAdmin() throws Exception {
        mvc.perform(get("/api/admin/evaluation/dataset")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/evaluation/dataset").header("Authorization", "Bearer " + login())).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)", "admin", new BCryptPasswordEncoder().encode("admin-password"));
        jdbc.update("INSERT INTO mf_role VALUES (2, 'ADMIN', '管理员', TRUE)");
        jdbc.update("INSERT INTO mf_user_role VALUES (2, 2)");
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String admin=new ObjectMapper().readTree(result.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/admin/evaluation/dataset").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datasetVersion").value("1.0.0"))
                .andExpect(jsonPath("$.cases.length()").value(11))
                .andExpect(jsonPath("$.cases[0].transcript[0].index").value(0));
    }

    @Test void onlyAdministratorsManageUsersAndRoles() throws Exception {
        String ordinary = login();
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + ordinary)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)", "admin", new BCryptPasswordEncoder().encode("admin-password"));
        jdbc.update("INSERT INTO mf_role VALUES (2, 'ADMIN', '管理员', TRUE)");
        jdbc.update("INSERT INTO mf_user_role VALUES (2, 2)");
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String admin = new ObjectMapper().readTree(result.getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/admin/roles").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"code\":\"经理\",\"name\":\"经理\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("角色编码须以大写英文字母开头")));
        mvc.perform(post("/api/admin/roles").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"code\":\"MANAGER\",\"name\":\"经理\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/admin/roles/2").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"name\":\"管理员\",\"enabled\":false}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/roles/1").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"name\":\"普通成员\",\"enabled\":true}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
        mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"username\":\"new-user\",\"password\":\"long-password-123\"}"))
                .andExpect(status().isCreated());
        String hash = jdbc.queryForObject("SELECT password_hash FROM mf_user WHERE username='new-user'", String.class);
        org.junit.jupiter.api.Assertions.assertTrue(new BCryptPasswordEncoder().matches("long-password-123", hash));
        mvc.perform(put("/api/admin/users/1/roles").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"roleIds\":[2]}" )).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + ordinary)).andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/2/status").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/1/roles").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"roleIds\":[]}" )).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + ordinary)).andExpect(status().isForbidden());
    }

    @Test void meetingsAreVisibleOnlyToTheirOwner() throws Exception {
        String ownerToken = login();
        var response = mvc.perform(post("/api/meetings").header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"title\":\"项目讨论\",\"meetingTime\":\"2026-11-10T14:00:00\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("项目讨论")).andReturn().getResponse();
        String id = new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        mvc.perform(post("/api/meetings").header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json").content("{\"title\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/meetings").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)", "other", new BCryptPasswordEncoder().encode("other-password"));
        var other = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"other\",\"password\":\"other-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String otherToken = new ObjectMapper().readTree(other.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/meetings/" + id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/meetings").header("Authorization", "Bearer " + otherToken)).andExpect(content().json("[]"));
        mvc.perform(get("/api/meetings/" + id)).andExpect(status().isUnauthorized());
    }

    @Test void transcriptVersionsAndMemberPermissions() throws Exception {
        String owner = login();
        var response = mvc.perform(post("/api/meetings").header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"title\":\"测试会议\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id = new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)", "other", new BCryptPasswordEncoder().encode("other-password"));
        var otherResponse = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"other\",\"password\":\"other-password\"}")).andReturn().getResponse();
        String other = new ObjectMapper().readTree(otherResponse.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/meetings/" + id + "/members").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/meetings/" + id + "/eligible-users").param("query", "oth").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().json("[\"other\"]"));
        mvc.perform(get("/api/meetings/" + id + "/eligible-users").param("query", "%").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/meetings/" + id + "/eligible-users").param("query", "test").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/meetings/" + id + "/members").header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"username\":\"other\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/meetings/" + id + "/members").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.username == 'test-user')].memberRole").value(org.hamcrest.Matchers.hasItem("ORGANIZER")))
                .andExpect(jsonPath("$[?(@.username == 'other')].memberRole").value(org.hamcrest.Matchers.hasItem("EDITOR")));
        mvc.perform(get("/api/meetings/" + id + "/members").header("Authorization", "Bearer " + other)).andExpect(status().isOk());
        mvc.perform(get("/api/meetings/" + id + "/eligible-users").param("query", "oth").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/meetings/" + id + "/transcript").header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"content\":\"初稿\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/meetings/" + id + "/transcript").header("Authorization", "Bearer " + other)
                .contentType("application/json").content("{\"content\":\"修订稿\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(post("/api/meetings/" + id + "/transcript").header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"content\":\"旧版本覆盖\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/meetings/" + id + "/transcript?version=1").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("初稿"));
        mvc.perform(put("/api/meetings/" + id + "/members/other").header("Authorization", "Bearer " + other)
                .contentType("application/json").content("{\"expectedRole\":\"EDITOR\",\"newRole\":\"VIEWER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/meetings/" + id + "/members/other").header("Authorization", "Bearer " + owner)
                .contentType("application/json").content("{\"expectedRole\":\"EDITOR\",\"newRole\":\"VIEWER\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/meetings/" + id + "/members/other?expectedRole=EDITOR").header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/meetings/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isOk());
        mvc.perform(get("/api/meetings/" + id + "/transcript").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("修订稿"));
        mvc.perform(post("/api/meetings/" + id + "/transcript").header("Authorization", "Bearer " + other)
                .contentType("application/json").content("{\"content\":\"无权修改\",\"expectedVersion\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/meetings/" + id + "/members/test-user?expectedRole=VIEWER").header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/meetings/" + id + "/members/other?expectedRole=VIEWER").header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/meetings/" + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/meetings/" + id + "/transcript").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mf_meeting_member_audit WHERE meeting_id = ?", Integer.class, id));
    }

    @Test void outlineGenerationEditConfirmationAndStaleness() throws Exception {
        String token=login();
        var meeting=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"大纲测试\"}")).andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(meeting.getContentAsString()).get("id").asText();
        mvc.perform(post("/api/meetings/"+id+"/transcript").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"content\":\"讨论预算\",\"expectedVersion\":0}")).andExpect(status().isOk());
        when(outlineGenerator.generate(org.mockito.ArgumentMatchers.eq("讨论预算"),org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> { modelRequest(inv.getArgument(1), false); return java.util.List.of("预算讨论"); });
        mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").value("预算讨论"));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":1,\"items\":[\"预算及时间\"],\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(BusinessStatus.CONFIRMED.code()));
        mvc.perform(get("/api/meetings/"+id+"/outline?version=1").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").value("预算讨论"));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":1,\"items\":[\"旧内容\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/meetings/"+id+"/transcript").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"content\":\"更新文字\",\"expectedVersion\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(true));
        when(outlineGenerator.generate(org.mockito.ArgumentMatchers.eq("更新文字"),org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> { try { modelRequest(inv.getArgument(1), true); } catch (RuntimeException ex) { throw new OutlineGenerator.OutlineGenerationException(ex); } throw new AssertionError(); });
        mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":2,\"expectedVersion\":2}"))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mf_ai_call WHERE prompt_version_id=1 AND status=4", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mf_ai_call WHERE prompt_version_id=1 AND status=5", Integer.class));
    }

    @Test void longTranscriptOutlineRetainsSourcesWhenReorderedEditedAndConfirmed() throws Exception {
        String token=login();
        var meeting=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"长会议\"}")).andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(meeting.getContentAsString()).get("id").asText();
        String transcript="预算讨论内容".repeat(2100);
        jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES(?,1,?,1)",id,transcript);
        var draft=new com.ylz.futuremade.meeting.model.dto.TranscriptOutlineDraft();
        draft.setTitles(java.util.List.of("预算","场地"));
        draft.setSource(new ObjectMapper().readValue("{\"groups\":[{\"title\":\"预算\",\"itemIds\":[\"1:0\"]},{\"title\":\"场地\",\"itemIds\":[\"1:1\"]}],\"items\":{\"1:0\":{\"content\":\"预算内容\",\"evidenceIndices\":[0]},\"1:1\":{\"content\":\"场地内容\",\"evidenceIndices\":[1]}},\"sourceSegments\":[{\"segmentIndex\":0,\"text\":\"预算原文\"},{\"segmentIndex\":1,\"text\":\"场地原文\"}]}", com.ylz.futuremade.meeting.model.dto.OutlineSourceSnapshot.class));
        when(transcriptOutlineGenerator.generate(org.mockito.ArgumentMatchers.eq(transcript),org.mockito.ArgumentMatchers.eq(id),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(draft);
        mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token).contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources.groups[0].itemIds[0]").value("1:0"));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"transcriptVersion\":1,\"expectedVersion\":1,\"items\":[\"场地调整\",\"预算\"],\"itemSourceIds\":[[\"1:1\"],[\"1:0\"]],\"confirm\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.sources.groups[0].title").value("场地调整"))
                .andExpect(jsonPath("$.sources.groups[0].itemIds[0]").value("1:1"));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"transcriptVersion\":1,\"expectedVersion\":2,\"items\":[\"场地调整\",\"预算\"],\"itemSourceIds\":[[\"unknown\"],[\"1:0\"]],\"confirm\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"transcriptVersion\":1,\"expectedVersion\":2,\"items\":[\"场地调整\",\"预算\"],\"itemSourceIds\":[[\"1:1\",\"1:0\"],[]],\"confirm\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.sources.groups[0].itemIds[1]").value("1:0"));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"transcriptVersion\":1,\"expectedVersion\":3,\"items\":[\"场地调整\",\"预算\"],\"itemSourceIds\":[[\"1:1\",\"1:0\"],[]],\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(4)).andExpect(jsonPath("$.sources.groups[0].itemIds[1]").value("1:0"));
    }

    @Test void promptVersionsRequireAdminAndPublishWithConflictProtection() throws Exception {
        String ordinary = login();
        mvc.perform(get("/api/admin/prompts").header("Authorization", "Bearer " + ordinary)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)", "admin", new BCryptPasswordEncoder().encode("admin-password"));
        jdbc.update("INSERT INTO mf_role VALUES (2, 'ADMIN', '管理员', TRUE)");
        jdbc.update("INSERT INTO mf_user_role VALUES (2, 2)");
        var loginResponse = mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String admin = new ObjectMapper().readTree(loginResponse.getContentAsString()).get("accessToken").asText();
        var created = mvc.perform(post("/api/admin/prompts/MEETING_OUTLINE/versions").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"content\":\"新版提示词\",\"changeNote\":\"改进格式\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2)).andReturn().getResponse();
        long versionId = new ObjectMapper().readTree(created.getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/admin/prompts/MEETING_OUTLINE/publish").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"versionId\":" + versionId + ",\"expectedActiveVersionId\":1,\"reason\":\"发布新版\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/admin/prompts/MEETING_OUTLINE/publish").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"versionId\":1,\"expectedActiveVersionId\":1,\"reason\":\"回滚\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/prompts/MEETING_OUTLINE/publish").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{\"versionId\":1,\"expectedActiveVersionId\":" + versionId + ",\"reason\":\"回滚\"}"))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mf_prompt_release_audit", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals("测试提示词", jdbc.queryForObject("SELECT content FROM mf_prompt_version WHERE id=1", String.class));
    }

    @Test void minutesFollowConfirmedOutlineAndEnforcePermissions() throws Exception {
        String token=login();
        var meeting=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"纪要测试\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(meeting.getContentAsString()).get("id").asText();
        mvc.perform(post("/api/meetings/"+id+"/transcript").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"content\":\"讨论预算\",\"expectedVersion\":0}")).andExpect(status().isOk());
        jdbc.update("INSERT INTO mf_outline_version(meeting_id,version,transcript_version,status,items_json,created_by) VALUES(?,1,1,1,'[\"预算\"]',1)",id);
        mvc.perform(post("/api/meetings/"+id+"/minutes/generate").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":1,\"expectedVersion\":0}")).andExpect(status().isConflict());
        jdbc.update("INSERT INTO mf_outline_version(meeting_id,version,transcript_version,status,items_json,created_by) VALUES(?,2,1,2,'[\"预算\"]',1)",id);
        var section = new com.ylz.futuremade.meeting.model.dto.MinutesSection();
        section.setOutlineId(1); section.setTitle("预算"); section.setContent("讨论了预算。"); section.setSourceSegmentIds(java.util.List.of());
        var document = new com.ylz.futuremade.meeting.model.dto.StructuredMinutes();
        document.setSections(java.util.List.of(section)); document.setActions(java.util.List.of());
        when(minutesGenerator.generate(org.mockito.ArgumentMatchers.eq("讨论预算"),org.mockito.ArgumentMatchers.eq(java.util.List.of("预算")),org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> { modelRequest(inv.getArgument(2), false); return document; });
        mvc.perform(post("/api/meetings/"+id+"/minutes/generate").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":2,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(BusinessStatus.DRAFT.code())).andExpect(jsonPath("$.outlineVersion").value(2));
        mvc.perform(post("/api/meetings/"+id+"/minutes").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":2,\"expectedVersion\":1,\"content\":\"修订后的纪要\",\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(BusinessStatus.CONFIRMED.code()));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mf_ai_generation_task WHERE meeting_id=? AND task_type='MINUTES_INDEX'", Integer.class, id));
        mvc.perform(get("/api/meetings/"+id+"/minutes?version=1").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.structured.sections[0].content").value("讨论了预算。"));
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)","viewer",new BCryptPasswordEncoder().encode("viewer-password"));
        jdbc.update("INSERT INTO mf_meeting_member VALUES (?,2,'VIEWER')",id);
        var other=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"viewer\",\"password\":\"viewer-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String viewer=new ObjectMapper().readTree(other.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/meetings/"+id+"/minutes").header("Authorization","Bearer "+viewer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/meetings/"+id+"/transcript").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"content\":\"更新文字稿\",\"expectedVersion\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/meetings/"+id+"/minutes").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.stale").value(true));
        mvc.perform(post("/api/meetings/"+id+"/minutes/generate").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":2,\"expectedVersion\":2}")).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mf_ai_call WHERE scenario='MEETING_MINUTES' AND prompt_version_id=2 AND status=4",Integer.class));
        var call=promptMapper.calls(20,0).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(2,call.getPromptVersionId());
        org.junit.jupiter.api.Assertions.assertEquals(BusinessStatus.SUCCEEDED.code(),call.getBusinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(1,call.getResultVersion());
        org.junit.jupiter.api.Assertions.assertEquals(2,call.getOutlineVersion());
    }

    @Test void minutesDoNotOverwriteChangesMadeDuringGeneration() throws Exception {
        String token=login();
        var meeting=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"并发测试\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(meeting.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES (?,1,'原文字稿',1)",id);
        jdbc.update("INSERT INTO mf_outline_version(meeting_id,version,transcript_version,status,items_json,created_by) VALUES(?,1,1,2,'[\"主题\"]',1)",id);
        when(minutesGenerator.generate(org.mockito.ArgumentMatchers.eq("原文字稿"),org.mockito.ArgumentMatchers.eq(java.util.List.of("主题")),org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            modelRequest(invocation.getArgument(2), false);
            jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES (?,2,'新文字稿',1)",id);
            var section = new com.ylz.futuremade.meeting.model.dto.MinutesSection();
            section.setOutlineId(1); section.setTitle("主题"); section.setContent("生成结果"); section.setSourceSegmentIds(java.util.List.of());
            var document = new com.ylz.futuremade.meeting.model.dto.StructuredMinutes();
            document.setSections(java.util.List.of(section)); document.setActions(java.util.List.of());
            return document;
        });
        mvc.perform(post("/api/meetings/"+id+"/minutes/generate").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":1,\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mf_minutes_version WHERE meeting_id=?",Integer.class,id));
        org.junit.jupiter.api.Assertions.assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mf_ai_call WHERE meeting_id=? AND status=4",Integer.class,id));
        var call=promptMapper.calls(20,0).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(BusinessStatus.SUCCEEDED.code(),call.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(BusinessStatus.FAILED.code(),call.getBusinessStatus());
        org.junit.jupiter.api.Assertions.assertNull(call.getResultVersion());
    }

    @Test void structuredMinutesSaveUserEditsAndPreserveHistoricalSources() throws Exception {
        String token = login();
        var response = mvc.perform(post("/api/meetings").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"title\":\"结构化纪要\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id = new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES (?,1,'原文字稿',1)", id);
        jdbc.update("INSERT INTO mf_outline_version(meeting_id,version,transcript_version,status,items_json,created_by) VALUES(?,1,1,2,'[\"预算\"]',1)", id);
        String original = "{\"sections\":[{\"outlineId\":1,\"title\":\"预算\",\"content\":\"预算十万\",\"sourceSegmentIds\":[2],\"needsReview\":false}],\"actions\":[{\"outlineId\":1,\"content\":\"核对预算\",\"assignee\":\"小王\",\"deadline\":null,\"sourceSegmentIds\":[2],\"needsReview\":false}]}";
        jdbc.update("INSERT INTO mf_minutes_version(meeting_id,version,outline_version,transcript_version,status,content,structured_json,created_by) VALUES(?,1,1,1,1,'原纪要',?,1)", id, original);
        String edited = "{\"outlineVersion\":1,\"expectedVersion\":1,\"confirm\":false,\"structured\":{\"sections\":[{\"outlineId\":1,\"title\":\"伪造标题\",\"content\":\"预算八万\",\"sourceSegmentIds\":[999],\"needsReview\":false}],\"actions\":[{\"outlineId\":1,\"content\":\"核对预算\",\"assignee\":\"小李\",\"sourceSegmentIds\":[999],\"needsReview\":false}]}}";
        mvc.perform(post("/api/meetings/" + id + "/minutes").header("Authorization", "Bearer " + token).contentType("application/json").content(edited))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.structured.sections[0].title").value("预算"))
                .andExpect(jsonPath("$.structured.sections[0].content").value("预算八万"))
                .andExpect(jsonPath("$.structured.sections[0].sourceSegmentIds[0]").value(2))
                .andExpect(jsonPath("$.structured.sections[0].needsReview").value(true))
                .andExpect(jsonPath("$.structured.actions[0].assignee").value("小李"))
                .andExpect(jsonPath("$.structured.actions[0].needsReview").value(true));
        mvc.perform(get("/api/meetings/" + id + "/minutes?version=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.structured.sections[0].content").value("预算十万"));
        String confirm = edited.replace("\"expectedVersion\":1", "\"expectedVersion\":2").replace("\"confirm\":false", "\"confirm\":true");
        mvc.perform(post("/api/meetings/" + id + "/minutes").header("Authorization", "Bearer " + token).contentType("application/json").content(confirm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.status").value(2))
                .andExpect(jsonPath("$.structured.sections[0].content").value("预算八万"));
    }

    @Test void unchangedSavesDoNotCreateVersionsButConfirmStillDoes() throws Exception {
        String token=login();
        var response=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"保存测试\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES (?,1,?,1)",id,"一行\n二行");
        mvc.perform(post("/api/meetings/"+id+"/transcript").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"content\":\"  一行\\r\\n二行  \",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        jdbc.update("INSERT INTO mf_outline_version(meeting_id,version,transcript_version,status,items_json,created_by) VALUES(?,1,1,1,'[\"一\",\"二\"]',1)",id);
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":1,\"items\":[\" 一 \",\"二\"],\"confirm\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":1,\"items\":[\"二\",\"一\"],\"confirm\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(post("/api/meetings/"+id+"/outline").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":2,\"items\":[\"二\",\"一\"],\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.status").value(BusinessStatus.CONFIRMED.code()));
        jdbc.update("INSERT INTO mf_minutes_version(meeting_id,version,outline_version,transcript_version,status,content,created_by) VALUES(?,1,3,1,1,?,1)",id,"正文\n第二行");
        mvc.perform(post("/api/meetings/"+id+"/minutes").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":3,\"expectedVersion\":1,\"content\":\" 正文\\r\\n第二行 \",\"confirm\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/meetings/"+id+"/minutes").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":3,\"expectedVersion\":1,\"content\":\"正文\\n第二行\",\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.status").value(BusinessStatus.CONFIRMED.code()));
        jdbc.update("INSERT INTO mf_outline_version(meeting_id,version,transcript_version,status,items_json,created_by) VALUES(?,4,1,2,'[\"二\",\"一\"]',1)",id);
        mvc.perform(post("/api/meetings/"+id+"/minutes").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"outlineVersion\":4,\"expectedVersion\":2,\"content\":\"正文\\n第二行\",\"confirm\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.outlineVersion").value(4));
        mvc.perform(post("/api/meetings/"+id+"/transcript").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"content\":\"一行\\n二行\",\"expectedVersion\":0}")).andExpect(status().isConflict());
    }

    @Test void repeatedGenerationKeyReplaysResultWithoutAnotherModelCall() throws Exception {
        String token=login();
        var response=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"幂等测试\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES (?,1,'幂等文字稿',1)",id);
        when(outlineGenerator.generate(org.mockito.ArgumentMatchers.eq("幂等文字稿"),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of("幂等议题"));
        String key=java.util.UUID.randomUUID().toString();
        String body="{\"transcriptVersion\":1,\"expectedVersion\":0}";
        for(int i=0;i<2;i++) mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token).header("Idempotency-Key",key)
                .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        org.mockito.Mockito.verify(outlineGenerator,org.mockito.Mockito.times(1)).generate(org.mockito.ArgumentMatchers.eq("幂等文字稿"),org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token).header("Idempotency-Key",key)
                .contentType("application/json").content("{\"transcriptVersion\":1,\"expectedVersion\":1}")).andExpect(status().isConflict());
    }

    @Test void simultaneousGenerationWithSameKeyCallsModelOnce() throws Exception {
        String token=login();
        var response=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"title\":\"并发幂等测试\"}")).andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_transcript_version(meeting_id,version,content,created_by) VALUES (?,1,'并发文字稿',1)",id);
        var started=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(outlineGenerator.generate(org.mockito.ArgumentMatchers.eq("并发文字稿"),org.mockito.ArgumentMatchers.any())).thenAnswer(invocation->{started.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS);return java.util.List.of("并发议题");});
        String key=java.util.UUID.randomUUID().toString(),body="{\"transcriptVersion\":1,\"expectedVersion\":0}";
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var first=executor.submit(()->mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token).header("Idempotency-Key",key)
                    .contentType("application/json").content(body)).andReturn().getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
            mvc.perform(post("/api/meetings/"+id+"/outline/generate").header("Authorization","Bearer "+token).header("Idempotency-Key",key)
                    .contentType("application/json").content(body)).andExpect(status().isTooEarly());
            release.countDown();
            org.junit.jupiter.api.Assertions.assertEquals(200,first.get(5,java.util.concurrent.TimeUnit.SECONDS));
            org.mockito.Mockito.verify(outlineGenerator,org.mockito.Mockito.times(1)).generate(org.mockito.ArgumentMatchers.eq("并发文字稿"),org.mockito.ArgumentMatchers.any());
        } finally {release.countDown();executor.shutdownNow();}
    }

    @Test void recordsReturnedUsageAndLeavesMissingUsageUnknown() {
        jdbc.update("INSERT INTO mf_ai_call(id,scenario,prompt_version_id,meeting_id,transcript_version,actor_id,status) VALUES(101,'MEETING_OUTLINE',1,'meeting',1,1,3)");
        var details=new org.springframework.ai.deepseek.api.DeepSeekApi.Usage.PromptTokensDetails(35);
        var nativeUsage=new org.springframework.ai.deepseek.api.DeepSeekApi.Usage(20,100,120,details);
        var usage=new org.springframework.ai.chat.metadata.DefaultUsage(100,20,120,nativeUsage);
        var metadata=org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().id("request-1").model("test-model").usage(usage).build();
        promptService.recordMetadata(101,new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(),metadata));
        var call=promptMapper.calls(20,0).get(0);
        org.junit.jupiter.api.Assertions.assertEquals("DeepSeek",call.getProvider());
        org.junit.jupiter.api.Assertions.assertEquals("test-model",call.getModelName());
        org.junit.jupiter.api.Assertions.assertEquals("request-1",call.getProviderRequestId());
        org.junit.jupiter.api.Assertions.assertEquals(100,call.getInputTokens());
        org.junit.jupiter.api.Assertions.assertEquals(20,call.getOutputTokens());
        org.junit.jupiter.api.Assertions.assertEquals(35,call.getCachedTokens());
        jdbc.update("INSERT INTO mf_ai_call(id,scenario,prompt_version_id,meeting_id,transcript_version,actor_id,status) VALUES(102,'MEETING_OUTLINE',1,'meeting',1,1,3)");
        var empty=org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(new org.springframework.ai.chat.metadata.EmptyUsage()).build();
        promptService.recordMetadata(102,new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(),empty));
        var missing=promptMapper.calls(20,0).get(0);
        org.junit.jupiter.api.Assertions.assertNull(missing.getInputTokens());
        org.junit.jupiter.api.Assertions.assertNull(missing.getCachedTokens());
    }

    @Test void callContentIsStoredAndOnlyAdminsCanReadIt() throws Exception {
        jdbc.update("INSERT INTO mf_ai_call(id,scenario,prompt_version_id,meeting_id,transcript_version,actor_id,status) VALUES(120,'MEETING_OUTLINE',1,'meeting',1,1,3)");
        promptService.recordInput(120,"系统指令","会议文字稿");
        org.junit.jupiter.api.Assertions.assertNull(promptService.content(120).getOutputContent());
        promptService.recordOutput(120,"未通过格式校验的原始输出");
        String ordinary=login();
        mvc.perform(get("/api/admin/prompts/calls/120/content").header("Authorization","Bearer "+ordinary)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)","admin",new BCryptPasswordEncoder().encode("admin-password"));
        jdbc.update("INSERT INTO mf_role VALUES (2, 'ADMIN', '管理员', TRUE)");jdbc.update("INSERT INTO mf_user_role VALUES (2, 2)");
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String admin=new ObjectMapper().readTree(result.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/admin/prompts/calls").header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].promptVersionId").value(1)).andExpect(jsonPath("$[0].versionId").value(1));
        mvc.perform(get("/api/admin/prompts/calls/120/content").header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.systemContent").value("系统指令"))
                .andExpect(jsonPath("$.userContent").value("会议文字稿"))
                .andExpect(jsonPath("$.outputContent").value("未通过格式校验的原始输出"));
        mvc.perform(get("/api/admin/prompts/calls/999/content").header("Authorization","Bearer "+admin)).andExpect(status().isNotFound());
    }

    @Test void transcriptionRequiresEditorAndConfiguration() throws Exception {
        String owner=login();
        var response=mvc.perform(post("/api/meetings").header("Authorization","Bearer "+owner)
                .contentType("application/json").content("{\"title\":\"转写权限测试\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        String id=new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
        jdbc.update("INSERT INTO mf_user VALUES (2, ?, ?, TRUE)","viewer",new BCryptPasswordEncoder().encode("viewer-password"));
        jdbc.update("INSERT INTO mf_meeting_member VALUES (?,2,'VIEWER')",id);
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"viewer\",\"password\":\"viewer-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String viewer=new ObjectMapper().readTree(result.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/meetings/"+id+"/transcriptions/latest").header("Authorization","Bearer "+viewer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/meetings/"+id+"/transcriptions").header("Authorization","Bearer "+viewer)
                .contentType("application/json").content("{\"audioUrl\":\"https://example.com/a.wav\",\"format\":\"wav\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/meetings/"+id+"/transcriptions").header("Authorization","Bearer "+owner)
                .contentType("application/json").content("{\"audioUrl\":\"https://example.com/a.wav\",\"format\":\"wav\"}")).andExpect(status().isServiceUnavailable());
        var file=new org.springframework.mock.web.MockMultipartFile("file","meeting.wav","audio/wav",new byte[]{1,2,3});
        mvc.perform(multipart("/api/meetings/"+id+"/transcriptions/upload").file(file).header("Authorization","Bearer "+viewer)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/meetings/"+id+"/transcriptions/upload").file(file).header("Authorization","Bearer "+owner)).andExpect(status().isServiceUnavailable());
    }

    private String login() throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertNull(response.getCookie("JSESSIONID"));
        return new ObjectMapper().readTree(response.getContentAsString()).get("accessToken").asText();
    }
}
