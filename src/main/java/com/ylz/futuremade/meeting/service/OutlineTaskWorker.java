package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.List;

/**
 * 执行大纲 Graph 的一个文字稿分段，保存可恢复的提取结果。
 */
@Service
@Slf4j
public class OutlineTaskWorker {
    private final AiGenerationBatchMapper batches;
    private final AiGenerationTaskMapper tasks;
    private final GenerationBatchLeaseService leases;
    private final OutlineService outlines;
    private final TranscriptOutlineGenerator generator;
    private final PromptService prompts;
    private final AdminMapper users;
    private final ObjectMapper json;
    private final int leaseSeconds;
    private final String model;

    public OutlineTaskWorker(AiGenerationBatchMapper batches, AiGenerationTaskMapper tasks, GenerationBatchLeaseService leases, OutlineService outlines,
                             TranscriptOutlineGenerator generator, PromptService prompts, AdminMapper users, ObjectMapper json,
                             @Value("${meetflow.outline.task-lease-seconds:${meetflow.minutes.task-lease-seconds:120}}") int leaseSeconds,
                             @Value("${meetflow.ai.default-model:deepSeekChatModel}") String model) {
        if (leaseSeconds < 3) throw new IllegalArgumentException("批次租约时间至少为三秒");
        this.batches = batches;
        this.tasks = tasks;
        this.leases = leases;
        this.outlines = outlines;
        this.generator = generator;
        this.prompts = prompts;
        this.users = users;
        this.json = json;
        this.leaseSeconds = leaseSeconds;
        this.model = model;
    }

    /**
     * 领取并执行可恢复分段；Graph 负责决定何时归并。
     */
    public boolean execute(String batchId, String owner) throws Exception {
        AiGenerationBatchModel part = batches.findById(batchId);
        if (part == null) return false;
        AiGenerationTaskModel task = tasks.findById(part.getTaskId());
        // 调度查询与执行之间任务可能被删除或状态发生变化，领取前重新确认。
        if (task == null) return false;
        if (!PromptScenarioCode.MEETING_OUTLINE.code().equals(task.getTaskType())) return false;
        if (!Objects.equals(task.getStatus(), GenerationTaskStatus.PENDING.code())
                && !Objects.equals(task.getStatus(), GenerationTaskStatus.RUNNING.code())) return false;
        String token = leases.claim(batchId, owner, leaseSeconds);
        if (token == null) return false;
        log.info("大纲分段已领取，taskId={}，batchId={}，batchNo={}", task.getId(), batchId, part.getBatchNo());
        AtomicBoolean lost = new AtomicBoolean(false);
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outline-batch-lease");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleWithFixedDelay(() -> {
            try {
                if (!leases.renew(batchId, token, leaseSeconds)) lost.set(true);
            } catch (RuntimeException ex) {
                lost.set(true);
                ErrorDiagnostics.error("大纲批次续租失败", ex);
            }
        }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
        try {
            if (!model.equals(task.getModelName())) throw new IllegalStateException("任务固定的模型配置已变化");
            AdminMapper.UserRow user = users.user(task.getActorId());
            if (user == null || !user.enabled())
                throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "任务发起用户已停用");
            outlines.checkGeneration(task.getMeetingId(), task.getActorId(), task.getTranscriptVersion(), task.getExpectedResultVersion());
            List<ChunkUtterance> all = new ArrayList<>();
            for (AiGenerationBatchModel batch : batches.findByTask(task.getId())) {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(batch.getInputJson().getBytes(StandardCharsets.UTF_8)));
                if (!Objects.equals(hash, batch.getInputHash()))
                    throw new IllegalStateException("大纲分段快照校验失败");
                TranscriptChunkView chunk = json.readValue(batch.getInputJson(), TranscriptChunkView.class);
                all.addAll(chunk.getUtterances());
            }
            TranscriptChunkView chunk = json.readValue(part.getInputJson(), TranscriptChunkView.class);
            com.ylz.futuremade.prompt.model.entity.PromptVersion prompt = prompts.version(PromptScenarioCode.TRANSCRIPT_EXTRACTION.code(), task.getPromptVersionId());
            ChunkExtractionView result = generator.extractChunk(chunk, all, prompt, task.getMeetingId(), task.getTranscriptVersion(), task.getActorId(), user.username(), batchId);
            if (lost.get()) return false;
            boolean completed = leases.complete(batchId, token, json.writeValueAsString(result), null);
            log.info("大纲分段写入结束，taskId={}，batchId={}，accepted={}，candidateCount={}",
                    task.getId(), batchId, completed, result.getItems().size());
            return completed;
        } catch (Exception ex) {
            log.warn("大纲分段失败，taskId={}，batchId={}，exceptionType={}", task.getId(), batchId, ex.getClass().getName());
            boolean changed = ex instanceof ResponseStatusException status && (status.getStatusCode().value() == 403 || status.getStatusCode().value() == 404 || status.getStatusCode().value() == 409);
            String code = changed ? "SOURCE_CHANGED" : "BATCH_FAILED";
            // 保留失败批次供用户重试，写入失败不能覆盖原始异常。
            try {
                if (!lost.get()) leases.fail(batchId, token, code, null);
            } catch (RuntimeException persistenceFailure) {
                ex.addSuppressed(persistenceFailure);
            }
            throw ex;
        } finally {
            heartbeat.shutdownNow();
        }
    }
}
