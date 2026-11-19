package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.ModelOutputTruncatedException;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.dto.MinutesOutlineInput;
import com.ylz.futuremade.meeting.model.dto.MinutesTaskConfig;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 执行单个已持久化的纪要批次；调度器负责发现需要处理的批次。 */
@Service
public class MinutesBatchWorker {
    private final AiGenerationBatchMapper batches;
    private final AiGenerationTaskMapper tasks;
    private final GenerationBatchLeaseService leases;
    private final MinutesBatchSplitService splits;
    private final MinutesService minutes;
    private final OutlineService outlines;
    private final MinutesBatchGenerator generator;
    private final PromptService prompts;
    private final AdminMapper users;
    private final ObjectMapper json;
    private final int leaseSeconds;
    private final String currentModel;

    public MinutesBatchWorker(AiGenerationBatchMapper batches, AiGenerationTaskMapper tasks, GenerationBatchLeaseService leases, MinutesBatchSplitService splits,
            MinutesService minutes, OutlineService outlines, MinutesBatchGenerator generator, PromptService prompts,
            AdminMapper users, ObjectMapper json, @Value("${meetflow.minutes.task-lease-seconds:120}") int leaseSeconds,
            @Value("${meetflow.ai.default-model:deepSeekChatModel}") String currentModel) {
        this.batches = batches; this.tasks = tasks; this.leases = leases; this.splits = splits; this.minutes = minutes; this.outlines = outlines;
        this.generator = generator; this.prompts = prompts; this.users = users; this.json = json;
        if (leaseSeconds < 3) throw new IllegalArgumentException("批次租约时间至少为三秒");
        this.leaseSeconds = leaseSeconds; this.currentModel = currentModel;
    }

    /** 尝试领取并执行一个批次；领取失败表示已有其他工作者处理。 */
    public boolean execute(String batchId, String workerId) {
        var batch = batches.findById(batchId);
        if (batch == null) return false;
        var task = tasks.findById(batch.getTaskId());
        if (task == null || !PromptScenarioCode.MEETING_MINUTES.code().equals(task.getTaskType())
                || !(Objects.equals(task.getStatus(), GenerationTaskStatus.PENDING.code()) || Objects.equals(task.getStatus(), GenerationTaskStatus.RUNNING.code()))) return false;
        String token = leases.claim(batchId, workerId, leaseSeconds);
        if (token == null) return false;
        var lost = new AtomicBoolean(false);
        var heartbeat = Executors.newSingleThreadScheduledExecutor(r -> { var thread = new Thread(r, "minutes-batch-lease"); thread.setDaemon(true); return thread; });
        heartbeat.scheduleWithFixedDelay(() -> {
            try { if (!leases.renew(batchId, token, leaseSeconds)) lost.set(true); }
            catch (RuntimeException ex) { lost.set(true); ErrorDiagnostics.error("纪要批次续租失败", ex); }
        }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
        try {
            tasks.transition(task.getId(), GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), null);
            if (!Objects.equals(task.getModelName(), currentModel)) throw new IllegalStateException("任务固定的模型配置已变化");
            var user = users.user(task.getActorId());
            if (user == null || !user.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "任务发起用户已停用");
            var outline = minutes.checkGeneration(task.getMeetingId(), task.getActorId(), task.getOutlineVersion(), task.getExpectedResultVersion(), outlines);
            if (!Objects.equals(task.getTranscriptVersion(), outline.getTranscriptVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿版本已变化");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(batch.getInputJson().getBytes(StandardCharsets.UTF_8)));
            if (!Objects.equals(digest, batch.getInputHash())) throw new IllegalStateException("批次输入快照校验失败");
            List<MinutesOutlineInput> input = json.readValue(batch.getInputJson(), new TypeReference<List<MinutesOutlineInput>>() {});
            var config = json.readValue(task.getConfigJson(), MinutesTaskConfig.class);
            var prompt = prompts.version(PromptScenarioCode.MEETING_MINUTES.code(), task.getPromptVersionId());
            var execution = generator.generateSingleBatch(input, outline.getSources(), prompt, task.getMeetingId(), outline,
                    task.getActorId(), user.username(), batchId, config);
            if (lost.get()) return false;
            return leases.complete(batchId, token, json.writeValueAsString(execution.getResult()), execution.getAiCallId());
        } catch (ModelOutputTruncatedException ex) {
            try {
                if (!lost.get() && splits.split(batch, token)) return true;
            } catch (Exception splitError) {
                ErrorDiagnostics.error("纪要批次拆分失败", splitError);
            }
            if (!lost.get()) leases.fail(batchId, token, "OUTPUT_TRUNCATED", null);
            return false;
        } catch (Exception ex) {
            ErrorDiagnostics.error("后台纪要批次执行失败", ex);
            if (!lost.get()) leases.fail(batchId, token, errorCode(ex), null);
            return false;
        } finally {
            heartbeat.shutdownNow();
        }
    }

    private String errorCode(Exception ex) {
        if (ex instanceof ModelOutputTruncatedException) return "OUTPUT_TRUNCATED";
        if (ex instanceof ResponseStatusException status && (status.getStatusCode().value() == 403 || status.getStatusCode().value() == 404)) return "ACCESS_DENIED";
        if (ex instanceof ResponseStatusException status && status.getStatusCode().value() == 409) return "SOURCE_CHANGED";
        return "BATCH_FAILED";
    }
}
