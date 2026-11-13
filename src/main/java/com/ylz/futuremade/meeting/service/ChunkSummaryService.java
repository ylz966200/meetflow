package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.mapper.ChunkSummaryMapper;
import com.ylz.futuremade.meeting.model.dto.ChunkSummaryView;
import com.ylz.futuremade.meeting.model.entity.ChunkSummaryModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 将同一录音全部有效分段的候选信息汇总为待审核草稿。
 */
@Service
public class ChunkSummaryService {
    private final MeetingService access;
    private final TranscriptionService transcriptions;
    private final ChunkExtractionService extractions;
    private final ChunkSummaryMapper summaries;
    private final PromptService prompts;
    private final AiModelGateway models;
    private final ObjectMapper json;
    private final int maxInputChars;

    public ChunkSummaryService(MeetingService access, TranscriptionService transcriptions, ChunkExtractionService extractions,
                               ChunkSummaryMapper summaries, PromptService prompts, AiModelGateway models, ObjectMapper json,
                               @Value("${meetflow.ai.summary-max-input-chars:60000}") int maxInputChars) {
        this.access = access;
        this.transcriptions = transcriptions;
        this.extractions = extractions;
        this.summaries = summaries;
        this.prompts = prompts;
        this.models = models;
        this.json = json;
        this.maxInputChars = maxInputChars;
    }

    public ChunkSummaryView generate(String meetingId, String taskId, long actor, String key) {
        access.requireOutlineEditor(meetingId, actor, false);
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求标识必须是标准 UUID");
        }
        var existing = summaries.find(key);
        if (existing != null) return replay(existing, meetingId, taskId, actor);
        var chunks = transcriptions.previewChunks(meetingId, taskId, actor);
        var results = extractions.completed(meetingId, taskId, actor);
        if (chunks.isEmpty() || results.size() != chunks.size())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "存在未完成或已过期的分段提取，请先完成全部分段");
        try {
            String input = json.writeValueAsString(results);
            if (input.length() > maxInputChars)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "候选信息超过单次汇总长度限制，暂不支持自动分层汇总");
            var prompt = prompts.active(PromptScenarioCode.TRANSCRIPT_SUMMARY.code());
            var row = new ChunkSummaryModel();
            row.setId(key);
            row.setMeetingId(meetingId);
            row.setTaskId(taskId);
            row.setActorId(actor);
            row.setSourceHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(results))));
            row.setStatus(BusinessStatus.RUNNING.code());
            try {
                summaries.insert(row);
            } catch (DuplicateKeyException ex) {
                return replay(summaries.find(key), meetingId, taskId, actor);
            }
            Long callId = null;
            long start = System.nanoTime();
            try {
                callId = prompts.startCall(prompt, meetingId, null, actor, key, null);
                prompts.recordInput(callId, prompt.getContent(), input);
                var response = models.call(prompt.getContent(), input);
                String output = response.getResult().getOutput().getText();
                prompts.recordOutput(callId, output);
                prompts.recordMetadata(callId, response);
                if (response.hasFinishReasons(java.util.Set.of("length", "LENGTH")) || output == null || output.isBlank() || output.length() > 60000)
                    throw new IllegalArgumentException("汇总结果为空、过长或被截断");
                if (summaries.finish(key, BusinessStatus.SUCCEEDED.code(), callId, output) != 1)
                    throw new IllegalStateException("汇总状态异常");
                prompts.finishCall(callId, BusinessStatus.SUCCEEDED.code(), (System.nanoTime() - start) / 1_000_000, null);
                return replay(summaries.find(key), meetingId, taskId, actor);
            } catch (Exception ex) {
                if (callId != null)
                    prompts.finishCall(callId, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000, "SUMMARY_FAILED");
                summaries.finish(key, BusinessStatus.FAILED.code(), callId, null);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "汇总失败，请检查模型配置后重试");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("分段汇总请求无法处理", ex);
        }
    }

    private ChunkSummaryView replay(ChunkSummaryModel row, String meetingId, String taskId, long actor) {
        if (row == null || !meetingId.equals(row.getMeetingId()) || !taskId.equals(row.getTaskId()) || row.getActorId() != actor)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求标识已被其他请求使用");
        if (BusinessStatus.RUNNING.code().equals(row.getStatus()))
            throw new ResponseStatusException(HttpStatus.TOO_EARLY, "汇总仍在处理中，请稍后重试");
        if (!BusinessStatus.SUCCEEDED.code().equals(row.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "汇总已失败，请重新发起");
        var result = new ChunkSummaryView();
        result.setId(row.getId());
        result.setTaskId(row.getTaskId());
        result.setAiCallId(row.getAiCallId());
        result.setContent(row.getContent());
        return result;
    }
}
