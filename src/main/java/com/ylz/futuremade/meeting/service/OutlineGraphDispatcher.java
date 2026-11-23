package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/** 使用数据库扫描发现并恢复已持久化的大纲生成任务。 */
@Slf4j
@Component
public class OutlineGraphDispatcher {
    private final AiGenerationTaskMapper tasks;
    private final OutlineGenerationWorkflow workflow;
    private final boolean enabled;
    private final int scanSize;
    private final String owner = UUID.randomUUID().toString();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor;
    private final Semaphore slots;

    public OutlineGraphDispatcher(AiGenerationTaskMapper tasks, OutlineGenerationWorkflow workflow,
            @Value("${meetflow.outline.background.enabled:${meetflow.minutes.background.enabled:false}}") boolean enabled,
            @Value("${meetflow.minutes.background.max-concurrent-tasks:2}") int maxConcurrent,
            @Value("${meetflow.minutes.background.scan-size:20}") int scanSize,
            @Value("${meetflow.minutes.background.poll-delay-ms:5000}") long delayMs,
            @Value("${meetflow.minutes.background.initial-delay-ms:5000}") long initialDelayMs) {
        if (maxConcurrent < 1 || scanSize < maxConcurrent || delayMs < 1 || initialDelayMs < 0)
            throw new IllegalArgumentException("大纲 Graph 调度配置无效");
        this.tasks = tasks; this.workflow = workflow; this.enabled = enabled;
        this.scanSize = scanSize;
        this.executor = Executors.newFixedThreadPool(maxConcurrent);
        this.slots = new Semaphore(maxConcurrent);
    }

    /** 提交未完成任务；跨实例执行权由数据库租约保护。 */
    @Scheduled(fixedDelayString = "${meetflow.minutes.background.poll-delay-ms:5000}",
            initialDelayString = "${meetflow.minutes.background.initial-delay-ms:5000}")
    public void dispatch() {
        if (!enabled) return;
        for (String taskId : tasks.runnableGraph(PromptScenarioCode.MEETING_OUTLINE.code(), GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), scanSize)) {
            if (!slots.tryAcquire()) break;
            if (!active.add(taskId)) { slots.release(); continue; }
            try {
                executor.execute(() -> {
                    try {
                        workflow.run(taskId, owner);
                    } catch (Exception exception) {
                        log.error("大纲 Graph 调度执行失败", exception);
                    } finally {
                        active.remove(taskId);
                        slots.release();
                    }
                });
            } catch (RuntimeException exception) {
                active.remove(taskId);
                slots.release();
                throw exception;
            }
        }
    }

    /** 停止周期调度和执行池，未完成任务将在下次启动时恢复。 */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
