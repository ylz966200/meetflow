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
import com.ylz.futuremade.meeting.model.dto.MinutesBatchExecutionResult;
import com.ylz.futuremade.meeting.model.dto.MinutesTaskConfig;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 为纪要 Graph 执行单个已持久化的批次，并在截断时原子拆分。 */
@Service
@Slf4j
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
    public boolean execute(String batchId, String workerId) throws Exception {
        AiGenerationBatchModel batch = batches.findById(batchId);
        if (batch == null) return false;
        AiGenerationTaskModel task = tasks.findById(batch.getTaskId());
        if (task == null || !PromptScenarioCode.MEETING_MINUTES.code().equals(task.getTaskType())
                || !Objects.equals(task.getStatus(), GenerationTaskStatus.RUNNING.code())) return false;
        String token = leases.claim(batchId, workerId, leaseSeconds);
        if (token == null) return false;
        log.info("纪要批次已领取，taskId={}，batchId={}，batchNo={}", task.getId(), batchId, batch.getBatchNo());
        AtomicBoolean lost = new AtomicBoolean(false);
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "minutes-batch-lease");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleWithFixedDelay(() -> {
            try { if (!leases.renew(batchId, token, leaseSeconds)) lost.set(true); }
            catch (RuntimeException ex) { lost.set(true); ErrorDiagnostics.error("纪要批次续租失败", ex); }
        }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
        try {
            if (!Objects.equals(task.getModelName(), currentModel)) throw new IllegalStateException("任务固定的模型配置已变化");
            AdminMapper.UserRow user = users.user(task.getActorId());
            if (user == null || !user.enabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "任务发起用户已停用");
            OutlineView outline = minutes.checkGeneration(task.getMeetingId(), task.getActorId(), task.getOutlineVersion(), task.getExpectedResultVersion(), outlines);
            if (!Objects.equals(task.getTranscriptVersion(), outline.getTranscriptVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿版本已变化");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(batch.getInputJson().getBytes(StandardCharsets.UTF_8)));
            if (!Objects.equals(digest, batch.getInputHash())) throw new IllegalStateException("批次输入快照校验失败");
            List<MinutesOutlineInput> input = json.readValue(batch.getInputJson(), new TypeReference<List<MinutesOutlineInput>>() {});
            MinutesTaskConfig config = json.readValue(task.getConfigJson(), MinutesTaskConfig.class);
            PromptVersion prompt = prompts.version(PromptScenarioCode.MEETING_MINUTES.code(), task.getPromptVersionId());
            MinutesBatchExecutionResult execution = generator.generateSingleBatch(input, outline.getSources(), prompt, task.getMeetingId(), outline,
                    task.getActorId(), user.username(), batchId, config);
            if (lost.get()) return false;
            boolean completed = leases.complete(batchId, token, json.writeValueAsString(execution.getResult()), execution.getAiCallId());
            log.info("纪要批次写入结束，taskId={}，batchId={}，accepted={}，aiCallId={}",
                    task.getId(), batchId, completed, execution.getAiCallId());
            return completed;
        } catch (ModelOutputTruncatedException ex) {
            log.warn("纪要批次输出截断，taskId={}，batchId={}，准备拆分", task.getId(), batchId);
            try {
                if (!lost.get() && splits.split(batch, token)) return true;
            } catch (Exception splitError) {
                ex.addSuppressed(splitError);
            }
            recordFailure(batchId, token, lost, ex);
            throw ex;
        } catch (Exception ex) {
            log.warn("纪要批次失败，taskId={}，batchId={}，exceptionType={}", task.getId(), batchId, ex.getClass().getName());
            recordFailure(batchId, token, lost, ex);
            throw ex;
        } finally {
            heartbeat.shutdownNow();
        }
    }

    private void recordFailure(String batchId, String token, AtomicBoolean lost, Exception failure) {
        // 保存安全错误码供重试，记录失败不能覆盖原始异常。
        try {
            if (!lost.get()) leases.fail(batchId, token, errorCode(failure), null);
        } catch (RuntimeException persistenceFailure) {
            failure.addSuppressed(persistenceFailure);
        }
    }

    private String errorCode(Exception ex) {
        if (ex instanceof ModelOutputTruncatedException) return "OUTPUT_TRUNCATED";
        if (ex instanceof ResponseStatusException status && (status.getStatusCode().value() == 403 || status.getStatusCode().value() == 404)) return "ACCESS_DENIED";
        if (ex instanceof ResponseStatusException status && status.getStatusCode().value() == 409) return "SOURCE_CHANGED";
        return "BATCH_FAILED";
    }
}
