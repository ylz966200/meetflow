package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 执行大纲后台任务的一个分段或归并批次。
 */
@Service
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
                             @Value("${meetflow.minutes.task-lease-seconds:120}") int leaseSeconds, @Value("${meetflow.ai.default-model:deepSeekChatModel}") String model) {
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
     * 领取并执行可恢复批次；归并阶段只在全部分段完成后开始。
     */
    public boolean execute(String batchId, String owner) {
        var part = batches.findById(batchId);
        if (part == null) return false;
        var task = tasks.findById(part.getTaskId());
        // 调度查询与执行之间任务可能被删除或状态发生变化，领取前重新确认。
        if (task == null) return false;
        if (!PromptScenarioCode.MEETING_OUTLINE.code().equals(task.getTaskType())) return false;
        if (!Objects.equals(task.getStatus(), GenerationTaskStatus.PENDING.code())
                && !Objects.equals(task.getStatus(), GenerationTaskStatus.RUNNING.code())) return false;
        if (OutlineTaskService.GROUP_BATCH.equals(part.getBatchNo()) && batches.findByTask(task.getId()).stream().anyMatch(b -> !OutlineTaskService.GROUP_BATCH.equals(b.getBatchNo()) && b.getStatus() != GenerationBatchStatus.SUCCEEDED.code()))
            return false;
        String token = leases.claim(batchId, owner, leaseSeconds);
        if (token == null) return false;
        var lost = new AtomicBoolean(false);
        var heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "outline-batch-lease");
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
            tasks.transition(task.getId(), GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), null);
            if (!model.equals(task.getModelName())) throw new IllegalStateException("任务固定的模型配置已变化");
            var user = users.user(task.getActorId());
            if (user == null || !user.enabled())
                throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "任务发起用户已停用");
            outlines.checkGeneration(task.getMeetingId(), task.getActorId(), task.getTranscriptVersion(), task.getExpectedResultVersion());
            var all = new ArrayList<ChunkUtterance>();
            var results = new ArrayList<ChunkExtractionView>();
            for (var batch : batches.findByTask(task.getId())) {
                if (OutlineTaskService.GROUP_BATCH.equals(batch.getBatchNo())) continue;
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(batch.getInputJson().getBytes(StandardCharsets.UTF_8)));
                if (!Objects.equals(hash, batch.getInputHash()))
                    throw new IllegalStateException("大纲分段快照校验失败");
                var chunk = json.readValue(batch.getInputJson(), TranscriptChunkView.class);
                all.addAll(chunk.getUtterances());
                if (OutlineTaskService.GROUP_BATCH.equals(part.getBatchNo()))
                    results.add(json.readValue(batch.getOutputJson(), ChunkExtractionView.class));
            }
            Object result;
            if (OutlineTaskService.GROUP_BATCH.equals(part.getBatchNo())) {
                var config = json.readValue(task.getConfigJson(), OutlineTaskConfig.class);
                var prompt = prompts.version(PromptScenarioCode.TRANSCRIPT_TOPIC_GROUPING.code(), config.getGroupingPromptVersionId());
                result = generator.buildDraft(results, all, task.getTranscriptVersion(), task.getMeetingId(), task.getActorId(), batchId, prompt);
            } else {
                var chunk = json.readValue(part.getInputJson(), TranscriptChunkView.class);
                var prompt = prompts.version(PromptScenarioCode.TRANSCRIPT_EXTRACTION.code(), task.getPromptVersionId());
                result = generator.extractChunk(chunk, all, prompt, task.getMeetingId(), task.getTranscriptVersion(), task.getActorId(), user.username(), batchId);
            }
            if (lost.get()) return false;
            return leases.complete(batchId, token, json.writeValueAsString(result), null);
        } catch (Exception ex) {
            ErrorDiagnostics.error("后台大纲批次执行失败", ex);
            boolean changed = ex instanceof ResponseStatusException status && (status.getStatusCode().value() == 403 || status.getStatusCode().value() == 404 || status.getStatusCode().value() == 409);
            String code = changed ? "SOURCE_CHANGED" : "BATCH_FAILED";
            if (!lost.get()) leases.fail(batchId, token, code, null);
            return false;
        } finally {
            heartbeat.shutdownNow();
        }
    }
}
