package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.service.GenerationBatchLeaseService;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 使用共用批次租约执行纪要索引，并可在进程恢复后完成任务状态。
 */
@Service
public class KnowledgeIndexWorker {
    private final AiGenerationBatchMapper batches;
    private final AiGenerationTaskMapper tasks;
    private final GenerationBatchLeaseService leases;
    private final KnowledgeIndexTaskService indexTasks;
    private final MeetingKnowledgeService knowledge;
    private final AdminMapper users;
    private final int leaseSeconds;
    private final String modelId;

    public KnowledgeIndexWorker(AiGenerationBatchMapper batches, AiGenerationTaskMapper tasks, GenerationBatchLeaseService leases,
                                KnowledgeIndexTaskService indexTasks, MeetingKnowledgeService knowledge, AdminMapper users,
                                @Value("${meetflow.minutes.task-lease-seconds:120}") int leaseSeconds,
                                @Value("${meetflow.ai.doubao.embedding.model:}") String modelId) {
        if (leaseSeconds < 3) throw new IllegalArgumentException("批次租约时间至少为三秒");
        this.batches = batches;
        this.tasks = tasks;
        this.leases = leases;
        this.indexTasks = indexTasks;
        this.knowledge = knowledge;
        this.users = users;
        this.leaseSeconds = leaseSeconds;
        this.modelId = modelId;
    }

    /**
     * 领取批次并定时续租，失去租约后不更新批次状态。
     */
    public void execute(String batchId, String owner) {
        var batch = batches.findById(batchId);
        if (batch == null) return;
        var task = tasks.findById(batch.getTaskId());
        if (task == null) return;
        String token = leases.claim(batchId, owner, leaseSeconds);
        if (token == null) return;
        var lost = new AtomicBoolean(false);
        var heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "knowledge-index-lease");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleWithFixedDelay(() -> {
            try {
                if (!leases.renew(batchId, token, leaseSeconds)) lost.set(true);
            } catch (RuntimeException ex) {
                lost.set(true);
                ErrorDiagnostics.error("知识索引批次续租失败", ex);
            }
        }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
        try {
            tasks.transition(task.getId(), GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), null);
            var user = users.user(task.getActorId());
            if (user == null || !user.enabled()) throw new IllegalStateException("索引发起用户已停用");
            if (!modelId.equals(task.getModelName())) throw new IllegalStateException("任务固定的向量模型配置已变化");
            knowledge.index(task.getMeetingId(), task.getActorId(), indexTasks.version(task));
            if (!lost.get()) leases.complete(batchId, token, "{}", null);
        } catch (Exception ex) {
            ErrorDiagnostics.error("知识索引批次执行失败", ex);
            if (!lost.get()) leases.fail(batchId, token, "INDEX_FAILED", null);
        } finally {
            heartbeat.shutdownNow();
        }
    }

    /**
     * 批次状态已持久化时可重复汇总，处理执行成功后进程中断的情况。
     */
    public void finish(String taskId) {
        var task = tasks.findById(taskId);
        if (task == null || task.getStatus() != GenerationTaskStatus.RUNNING.code()) return;
        var parts = batches.findByTask(taskId);
        if (parts.stream().anyMatch(part -> part.getStatus() == GenerationBatchStatus.FAILED.code()))
            tasks.transition(taskId, GenerationTaskStatus.RUNNING.code(), GenerationTaskStatus.PARTIAL_FAILED.code(), "INDEX_FAILED");
        else if (!parts.isEmpty() && parts.stream().allMatch(part -> part.getStatus() == GenerationBatchStatus.SUCCEEDED.code()))
            tasks.complete(taskId, GenerationTaskStatus.RUNNING.code(), GenerationTaskStatus.SUCCEEDED.code(), indexTasks.version(task));
    }
}
