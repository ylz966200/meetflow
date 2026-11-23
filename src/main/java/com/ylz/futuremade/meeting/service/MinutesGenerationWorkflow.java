package com.ylz.futuremade.meeting.service;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.enums.MinutesTaskFailureCode;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeActionWithConfig.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;
import static com.ylz.futuremade.meeting.service.OutlineCheckpointSaver.LEASE_TOKEN;

/**
 * 编排纪要批次执行、拆分后的继续处理和最终事务汇总。
 */
@Service
public class MinutesGenerationWorkflow {
    private static final String GENERATE = "generateMinutesBatch";
    private static final String FINISH = "finishMinutes";
    private static final String WAIT = "waitForMinutesBatch";
    private static final String OWNER = "minutesLeaseOwner";
    // 最多 200 个大纲条目，二分产生的父批次和叶批次也低于这个步数。
    private static final int MAX_GRAPH_STEPS = 1000;

    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final MinutesBatchWorker worker;
    private final MinutesTaskFinalizer finalizer;
    private final OutlineCheckpointSaver checkpoints;
    private final int leaseSeconds;
    private final CompiledGraph graph;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();

    public MinutesGenerationWorkflow(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches,
                                     MinutesBatchWorker worker, MinutesTaskFinalizer finalizer, OutlineCheckpointSaver checkpoints,
                                     @Value("${meetflow.minutes.task-lease-seconds:120}") int leaseSeconds) throws Exception {
        if (leaseSeconds < 3) throw new IllegalArgumentException("纪要任务租约时间至少为三秒");
        this.tasks = tasks;
        this.batches = batches;
        this.worker = worker;
        this.finalizer = finalizer;
        this.checkpoints = checkpoints;
        this.leaseSeconds = leaseSeconds;

        Map<String, String> routes = Map.of(GENERATE, GENERATE, FINISH, FINISH, WAIT, StateGraph.END);
        this.graph = new StateGraph()
                .addNode(GENERATE, node_async((state, config) -> {
                    String taskId = config.threadId().orElseThrow();
                    String token = token(config);
                    requireLease(taskId, token);
                    List<String> available = runnable(taskId);
                    if (!available.isEmpty()) {
                        worker.execute(available.get(0), (String) config.metadata(OWNER).orElseThrow());
                    }
                    return Map.of();
                }))
                .addNode(FINISH, node_async((state, config) -> {
                    finalizer.finishIfReady(config.threadId().orElseThrow(), token(config));
                    return Map.of();
                }))
                .addConditionalEdges(StateGraph.START, edge_async((state, config) ->
                        route(config.threadId().orElseThrow())), routes)
                .addConditionalEdges(GENERATE, edge_async((state, config) ->
                        route(config.threadId().orElseThrow())), routes)
                .addEdge(FINISH, StateGraph.END)
                .compile(CompileConfig.builder().recursionLimit(MAX_GRAPH_STEPS).releaseThread(false)
                        .saverConfig(SaverConfig.builder().register(checkpoints).build()).build());
    }

    /**
     * 先取得新租约，再恢复最近节点；已经提交的批次结果始终从业务表读取。
     */
    public void run(String taskId, String owner) throws Exception {
        String attemptToken = UUID.randomUUID().toString();
        if (tasks.claimGraph(taskId, PromptScenarioCode.MEETING_MINUTES.code(), GenerationTaskStatus.PENDING.code(),
                GenerationTaskStatus.RUNNING.code(), owner, attemptToken, leaseSeconds) != 1) return;

        Exception failure = null;
        ScheduledFuture<?> renewal = null;
        try {
            renewal = heartbeat.scheduleWithFixedDelay(() -> {
                try {
                    tasks.renewGraph(taskId, attemptToken, leaseSeconds);
                } catch (RuntimeException exception) {
                    ErrorDiagnostics.error("纪要 Graph 续租失败", exception);
                }
            }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);

            RunnableConfig config = RunnableConfig.builder().threadId(taskId)
                    .addMetadata(OWNER, owner).addMetadata(LEASE_TOKEN, attemptToken).build();
            Optional<Checkpoint> saved = checkpoints.get(config);
            if (saved.isPresent()) {
                if (StateGraph.END.equals(saved.get().getNextNodeId())) {
                    // 上次等待批次租约时正常退出，从起点重新读取批次状态。
                    if (tasks.saveOutlineCheckpoint(taskId, attemptToken, null) != 1) {
                        throw new IllegalStateException("纪要 Graph 执行权已失效");
                    }
                } else {
                    config = config.withResume();
                }
            }
            graph.invoke(Map.of(), config)
                    .orElseThrow(() -> new IllegalStateException("纪要 Graph 未返回执行结果"));
        } catch (Exception exception) {
            failure = exception;
            // 批次已经记录失败时由汇总服务归类，否则允许保留批次结果重试 Graph。
            try {
                if (batches.statusesByTask(taskId).contains(GenerationBatchStatus.FAILED.code())) {
                    finalizer.finishIfReady(taskId, attemptToken);
                } else {
                    tasks.failGraph(taskId, attemptToken, GenerationTaskStatus.RUNNING.code(),
                            GenerationTaskStatus.PARTIAL_FAILED.code(), MinutesTaskFailureCode.GRAPH_FAILED.code());
                }
            } catch (Exception persistenceFailure) {
                exception.addSuppressed(persistenceFailure);
            }
            throw exception;
        } finally {
            if (renewal != null) renewal.cancel(false);
            try {
                tasks.releaseGraph(taskId, attemptToken);
            } catch (RuntimeException releaseFailure) {
                if (failure == null) throw releaseFailure;
                failure.addSuppressed(releaseFailure);
            }
        }
    }

    private String route(String taskId) {
        List<Integer> statuses = batches.statusesByTask(taskId);
        if (statuses.contains(GenerationBatchStatus.FAILED.code())) return FINISH;
        if (!runnable(taskId).isEmpty()) return GENERATE;
        boolean waiting = statuses.contains(GenerationBatchStatus.PENDING.code())
                || statuses.contains(GenerationBatchStatus.RUNNING.code());
        return waiting ? WAIT : FINISH;
    }

    private List<String> runnable(String taskId) {
        return batches.runnable(taskId, GenerationBatchStatus.PENDING.code(), GenerationBatchStatus.RUNNING.code(), 1);
    }

    private void requireLease(String taskId, String token) {
        if (tasks.ownsGraph(taskId, token) != 1) throw new IllegalStateException("纪要 Graph 执行权已失效");
    }

    private static String token(RunnableConfig config) {
        return (String) config.metadata(LEASE_TOKEN).orElseThrow();
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdown();
    }
}
