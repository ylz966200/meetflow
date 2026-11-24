package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.enums.OutlineFailureCode;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.model.dto.GenerationTaskView;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionView;
import com.ylz.futuremade.meeting.model.dto.OutlineTaskConfig;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 创建、查询和重试会议大纲后台任务。
 */
@Service
@Slf4j
public class OutlineTaskService {
    /** 新任务固定的审核修正上限，避免恢复时受运行配置变化影响。 */
    @Value("${meetflow.outline.max-review-revisions:3}")
    private int maxReviewRevisions = 1;
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final MeetingService meetings;
    private final OutlineService outlines;
    private final TranscriptOutlineGenerator generator;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final String modelName;

    public OutlineTaskService(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches, MeetingService meetings, OutlineService outlines,
                              TranscriptOutlineGenerator generator, PromptService prompts, ObjectMapper json, @Value("${meetflow.ai.default-model:deepSeekChatModel}") String modelName) {
        this.tasks = tasks;
        this.batches = batches;
        this.meetings = meetings;
        this.outlines = outlines;
        this.generator = generator;
        this.prompts = prompts;
        this.json = json;
        this.modelName = modelName;
    }

    /**
     * 固定文字稿、大纲版本及两个提示词版本，并原子保存全部分段。
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerationTaskView create(String meetingId, long actor, int transcriptVersion, int expectedVersion, String key) throws Exception {
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求标识必须是标准 UUID");
        }
        meetings.requireOutlineEditor(meetingId, actor, false);
        AiGenerationTaskModel existing = tasks.findByRequestKey(key);
        if (existing != null)
            return view(checkExisting(existing, meetingId, actor, transcriptVersion, expectedVersion));
        outlines.checkGeneration(meetingId, actor, transcriptVersion, expectedVersion);
        MeetingMapper.Transcript transcript = meetings.getTranscript(meetingId, actor, transcriptVersion);
        List<TranscriptChunkView> chunks = generator.prepareChunks(transcript.content(), meetingId, transcriptVersion);
        PromptVersion extractPrompt = prompts.active(PromptScenarioCode.TRANSCRIPT_EXTRACTION.code());
        PromptVersion groupPrompt = prompts.active(PromptScenarioCode.TRANSCRIPT_TOPIC_GROUPING.code());
        OutlineTaskConfig config = new OutlineTaskConfig();
        config.setGroupingPromptVersionId(groupPrompt.getId());
        config.setReviewPromptVersionId(prompts.active(PromptScenarioCode.TRANSCRIPT_OUTLINE_REVIEW.code()).getId());
        if (maxReviewRevisions < 0 || maxReviewRevisions > 3) {
            throw new IllegalArgumentException("大纲自动修正轮数应在0至3之间");
        }
        config.setMaxReviewRevisions(maxReviewRevisions);
        AiGenerationTaskModel task = new AiGenerationTaskModel();
        task.setId(UUID.randomUUID().toString());
        task.setRequestKey(key);
        task.setMeetingId(meetingId);
        task.setTaskType(PromptScenarioCode.MEETING_OUTLINE.code());
        task.setStatus(GenerationTaskStatus.PENDING.code());
        task.setActorId(actor);
        task.setTranscriptVersion(transcriptVersion);
        task.setOutlineVersion(expectedVersion);
        task.setExpectedResultVersion(expectedVersion);
        task.setPromptVersionId(extractPrompt.getId());
        task.setModelName(modelName);
        task.setConfigJson(json.writeValueAsString(config));
        try {
            tasks.insert(task);
        } catch (DuplicateKeyException ex) {
            AiGenerationTaskModel concurrent = tasks.lockByRequestKey(key);
            if (concurrent == null) throw ex;
            return view(checkExisting(concurrent, meetingId, actor, transcriptVersion, expectedVersion));
        }
        for (int i = 0; i < chunks.size(); i++)
            insertBatch(task.getId(), String.format(java.util.Locale.ROOT, "%08d", i + 1), json.writeValueAsString(chunks.get(i)));
        log.info("大纲任务已创建（事务内），taskId={}，meetingId={}，batchCount={}", task.getId(), meetingId, chunks.size());
        return view(task);
    }

    private void insertBatch(String taskId, String order, String input) throws Exception {
        AiGenerationBatchModel batch = new AiGenerationBatchModel();
        batch.setId(UUID.randomUUID().toString());
        batch.setTaskId(taskId);
        batch.setBatchNo(order);
        batch.setStatus(GenerationBatchStatus.PENDING.code());
        batch.setInputJson(input);
        batch.setInputHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))));
        batches.insert(batch);
    }

    private AiGenerationTaskModel checkExisting(AiGenerationTaskModel task, String meetingId, long actor, int version, int expected) {
        if (!meetingId.equals(task.getMeetingId()) || !Objects.equals(task.getActorId(), actor) || !PromptScenarioCode.MEETING_OUTLINE.code().equals(task.getTaskType()) || !Objects.equals(task.getTranscriptVersion(), version) || !Objects.equals(task.getExpectedResultVersion(), expected))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求标识已用于其他任务");
        return task;
    }

    /**
     * 查询任务进度；不返回原文或批次快照。
     */
    public GenerationTaskView get(String meetingId, String id, long actor) {
        meetings.requireOutlineEditor(meetingId, actor, false);
        return view(require(tasks.findById(id), meetingId));
    }

    /**
     * 查询当前会议最近一次大纲生成任务。
     */
    public GenerationTaskView latest(String meetingId, long actor) {
        meetings.requireOutlineEditor(meetingId, actor, false);
        return view(require(tasks.findLatest(meetingId, PromptScenarioCode.MEETING_OUTLINE.code()), meetingId));
    }

    /**
     * 重试失败的分段或归并阶段，保留成功结果。
     */
    @Transactional
    public GenerationTaskView retry(String meetingId, String id, long actor) {
        meetings.requireOutlineEditor(meetingId, actor, false);
        AiGenerationTaskModel task = require(tasks.lockById(id), meetingId);
        if (!Objects.equals(task.getActorId(), actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅任务发起人可以重试");
        if (task.getStatus() != GenerationTaskStatus.PARTIAL_FAILED.code())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不能重试");
        boolean legacy = batches.findByTask(id).stream()
                .anyMatch(batch -> AiGenerationTaskMapper.LEGACY_OUTLINE_GROUP_BATCH.equals(batch.getBatchNo()));
        if (legacy) throw new ResponseStatusException(HttpStatus.CONFLICT, "旧版任务请重新生成大纲");
        int resetCount = batches.resetFailed(id, GenerationBatchStatus.FAILED.code(), GenerationBatchStatus.PENDING.code());
        if (resetCount == 0 && !OutlineFailureCode.GRAPH_FAILED.code().equals(task.getErrorCode()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有可重试的批次");
        if (resetCount > 0) tasks.resetOutlineCheckpoint(id);
        if (tasks.transition(id, GenerationTaskStatus.PARTIAL_FAILED.code(), GenerationTaskStatus.RUNNING.code(), null) != 1)
            throw new IllegalStateException("任务状态更新失败");
        task.setStatus(GenerationTaskStatus.RUNNING.code());
        task.setErrorCode(null);
        log.info("大纲任务已重试（事务内），taskId={}，resetBatches={}", id, resetCount);
        return view(task);
    }

    private AiGenerationTaskModel require(AiGenerationTaskModel task, String meetingId) {
        if (task == null || !meetingId.equals(task.getMeetingId()) || !PromptScenarioCode.MEETING_OUTLINE.code().equals(task.getTaskType()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        return task;
    }

    private GenerationTaskView view(AiGenerationTaskModel task) {
        GenerationTaskView view = new GenerationTaskView();
        view.setTaskId(task.getId());
        view.setStatus(task.getStatus());
        view.setResultVersion(task.getResultVersion());
        view.setErrorCode(task.getErrorCode());
        for (AiGenerationBatchModel batch : batches.findByTask(task.getId())) {
            if (batch.getStatus() == GenerationBatchStatus.SPLIT.code()) continue;
            view.setTotalBatches(view.getTotalBatches() + 1);
            if (batch.getStatus() == GenerationBatchStatus.SUCCEEDED.code())
                view.setSucceededBatches(view.getSucceededBatches() + 1);
            if (batch.getStatus() == GenerationBatchStatus.FAILED.code())
                view.setFailedBatches(view.getFailedBatches() + 1);
            if (batch.getStatus() == GenerationBatchStatus.FAILED.code()
                    && OutlineFailureCode.SUSPICIOUS_EMPTY.code().equals(batch.getErrorCode()))
                view.getSuspiciousChunks().add(Integer.parseInt(batch.getBatchNo()));
            // 旧归并批次不是分段提取结果，保留其状态统计但不读取候选字段。
            if (AiGenerationTaskMapper.LEGACY_OUTLINE_GROUP_BATCH.equals(batch.getBatchNo())) continue;
            if (batch.getStatus() == GenerationBatchStatus.SUCCEEDED.code()) {
                try {
                    ChunkExtractionView extraction = json.readValue(batch.getOutputJson(), ChunkExtractionView.class);
                    if (Boolean.TRUE.equals(extraction.getEmptyNeedsReview()))
                        view.getSuspiciousChunks().add(extraction.getChunkIndex());
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new IllegalStateException("读取分段结果失败", exception);
                }
            }
        }
        return view;
    }
}
