package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.knowledge.enums.KnowledgeTaskType;
import com.ylz.futuremade.knowledge.service.KnowledgeIndexWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

/**
 * 扫描会议生成后台任务并使用租约领取批次。
 */
@Component
public class GenerationTaskScheduler {
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final MinutesGenerationWorkflow minutesWorkflow;
    private final KnowledgeIndexWorker knowledgeWorker;
    private final String owner = UUID.randomUUID().toString();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool;
    private final int scanSize;
    private final boolean generationEnabled;

    public GenerationTaskScheduler(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches,
                                   MinutesGenerationWorkflow minutesWorkflow, KnowledgeIndexWorker knowledgeWorker,
                                   @Value("${meetflow.minutes.background.max-concurrent-tasks:2}") int maxConcurrent,
                                   @Value("${meetflow.minutes.background.scan-size:20}") int scanSize,
                                   @Value("${meetflow.minutes.background.enabled:false}") boolean generationEnabled) {
        if (maxConcurrent < 1 || scanSize < maxConcurrent || scanSize > 100)
            throw new IllegalArgumentException("后台任务并发或扫描数量配置无效");
        this.tasks = tasks;
        this.batches = batches;
        this.minutesWorkflow = minutesWorkflow;
        this.knowledgeWorker = knowledgeWorker;
        this.pool = Executors.newFixedThreadPool(maxConcurrent);
        this.scanSize = scanSize;
        this.generationEnabled = generationEnabled;
    }

    /**
     * 发现可执行的纪要和知识索引任务；纪要由 Graph 推进，索引按批次推进。
     */
    @Scheduled(fixedDelayString = "${meetflow.minutes.background.poll-delay-ms:5000}", initialDelayString = "${meetflow.minutes.background.initial-delay-ms:5000}")
    public void poll() {
        if (generationEnabled) {
            schedule(PromptScenarioCode.MEETING_MINUTES.code());
        }
        schedule(KnowledgeTaskType.MINUTES_INDEX.code());
    }

    private void schedule(String type) {
        boolean minutesTask = PromptScenarioCode.MEETING_MINUTES.code().equals(type);
        List<String> runnable = minutesTask
                ? tasks.runnableGraph(type, GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), scanSize)
                : tasks.runnable(type, GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), scanSize);
        for (String taskId : runnable) {
            if (!active.add(taskId)) continue;
            pool.execute(() -> {
                try {
                    if (minutesTask) {
                        minutesWorkflow.run(taskId, owner);
                    } else {
                        List<String> available = batches.runnable(taskId, GenerationBatchStatus.PENDING.code(),
                                GenerationBatchStatus.RUNNING.code(), 1);
                        if (!available.isEmpty()) knowledgeWorker.execute(available.get(0), owner);
                        knowledgeWorker.finish(taskId);
                    }
                } catch (Exception ex) {
                    ErrorDiagnostics.error(minutesTask ? "纪要 Graph 执行失败" : "知识索引任务执行失败", ex);
                } finally {
                    active.remove(taskId);
                }
            });
        }
    }

    /**
     * 关闭后台线程池，执行中的租约失效后由下次启动恢复。
     */
    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
