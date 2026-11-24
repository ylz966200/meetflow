package com.ylz.futuremade.meeting.service;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.enums.OutlineFailureCode;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionView;
import com.ylz.futuremade.meeting.model.dto.ChunkUtterance;
import com.ylz.futuremade.meeting.model.dto.OutlineTaskConfig;
import com.ylz.futuremade.meeting.model.dto.OutlineMergeInput;
import com.ylz.futuremade.meeting.model.dto.OutlineGroupingProgress;
import com.ylz.futuremade.meeting.model.dto.OutlineReviewResult;
import com.ylz.futuremade.meeting.model.dto.TopicCandidate;
import com.ylz.futuremade.meeting.model.entity.OutlineMergeResultModel;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import com.ylz.futuremade.meeting.model.dto.TranscriptOutlineDraft;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.service.PromptService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeActionWithConfig.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;
import com.ylz.futuremade.common.logging.WorkflowNodeLogging;
import static com.ylz.futuremade.meeting.service.OutlineCheckpointSaver.LEASE_TOKEN;
import static com.ylz.futuremade.meeting.service.OutlineCheckpointSaver.ROUND;

/**
 * 大纲生成主流程：执行分段、检查进度、归并并保存。
 */
@Slf4j
@Service
public class OutlineGenerationWorkflow {
    /**
     * Graph 最多执行的节点数，防止流程配置错误导致无限循环。
     */
    private static final int MAX_GRAPH_STEPS = 10000;
    private static final String OWNER = "outlineLeaseOwner";
    private static final String EXTRACT = "extract";
    private static final String MERGE = "mergeBatches";
    private static final String CROSS_MERGE = "crossMerge";
    private static final String SAVE = "save";
    private static final String REVIEW = "reviewOutline";
    private static final String REVISE = "reviseOutline";
    private static final String FAIL = "fail";
    private static final String WAIT = "wait";
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final OutlineTaskWorker worker;
    private final OutlineGraphFinalizer finalizer;
    private final TranscriptOutlineGenerator generator;
    private final PromptService prompts;
    private final AdminMapper users;
    private final OutlineService outlines;
    private final ObjectMapper json;
    private final CompiledGraph graph;
    private final OutlineCheckpointSaver checkpoints;
    private final TopicAnalysisService topics;
    private final OutlineMergeStore mergeStore;
    private final OutlineReviewAgent reviewer;
    private final int leaseSeconds;
    private final ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(1);

    public OutlineGenerationWorkflow(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches, OutlineTaskWorker worker,
                                     OutlineGraphFinalizer finalizer, TranscriptOutlineGenerator generator, PromptService prompts, AdminMapper users, OutlineService outlines, ObjectMapper json,
                                     OutlineCheckpointSaver checkpoints, TopicAnalysisService topics, OutlineMergeStore mergeStore, OutlineReviewAgent reviewer,
                                     @Value("${meetflow.outline.task-lease-seconds:${meetflow.minutes.task-lease-seconds:120}}") int leaseSeconds) throws Exception {
        if (leaseSeconds < 3) throw new IllegalArgumentException("Graph 租约时间至少为三秒");
        this.tasks = tasks;
        this.batches = batches;
        this.worker = worker;
        this.finalizer = finalizer;
        this.generator = generator;
        this.prompts = prompts;
        this.users = users;
        this.outlines = outlines;
        this.json = json;
        this.leaseSeconds = leaseSeconds;
        this.checkpoints = checkpoints;
        this.topics = topics;
        this.mergeStore = mergeStore;
        this.reviewer = reviewer;
        Map<String, String> mergeRoutes = Map.of(CROSS_MERGE, CROSS_MERGE, REVIEW, REVIEW);
        this.graph = new StateGraph(() -> Map.of(ROUND, new ReplaceStrategy()))
                .addNode(EXTRACT, node_async((state, config) -> {
                    String taskId = taskId(config);
                    requireLease(taskId, token(config));
                    extract(taskId, (String) config.metadata(OWNER).orElseThrow());
                    return Map.of();
                }))
                .addNode(MERGE, node_async((state, config) -> mergeStage(config, 0)))
                .addNode(CROSS_MERGE, node_async((state, config) -> mergeStage(config, state.<Integer>value(ROUND).orElseThrow() + 1)))
                .addNode(REVISE, node_async((state, config) ->
                        reviseStage(config, state.<Integer>value(ROUND).orElseThrow())))
                .addNode(REVIEW, node_async((state, config) -> {
                    reviewStage(config, state.<Integer>value(ROUND).orElseThrow());
                    return Map.of();
                }))
                .addNode(SAVE, node_async((state, config) -> {
                    saveDraft(config, state.<Integer>value(ROUND).orElseThrow());
                    return Map.of();
                }))
                .addNode(FAIL, node_async((state, config) -> {
                    finalizer.failIfNeeded(taskId(config), token(config));
                    return Map.of();
                }))
                .addEdge(StateGraph.START, EXTRACT)
                .addConditionalEdges(EXTRACT, edge_async((state, config) -> route(taskId(config))),
                        Map.of(EXTRACT, EXTRACT, MERGE, MERGE, FAIL, FAIL, WAIT, StateGraph.END))
                .addConditionalEdges(MERGE, edge_async(this::mergeRoute), mergeRoutes)
                .addConditionalEdges(CROSS_MERGE, edge_async(this::mergeRoute), mergeRoutes)
                .addConditionalEdges(REVIEW, edge_async((state, config) ->
                        reviewRoute(config, state.<Integer>value(ROUND).orElseThrow())),
                        Map.of(REVISE, REVISE, SAVE, SAVE))
                .addEdge(REVISE, REVIEW)
                .addEdge(SAVE, StateGraph.END)
                .addEdge(FAIL, StateGraph.END)
                .compile(CompileConfig.builder().recursionLimit(MAX_GRAPH_STEPS).releaseThread(false)
                        .saverConfig(SaverConfig.builder().register(checkpoints).build())
                        .withLifecycleListener(new WorkflowNodeLogging()).build());
    }

    /**
     * 先领取新租约，再从任务最近检查点恢复；租约令牌仅通过本次执行配置传递。
     */
    public void run(String taskId, String owner) throws Exception {
        String token = UUID.randomUUID().toString();
        if (tasks.claimGraph(taskId, PromptScenarioCode.MEETING_OUTLINE.code(), GenerationTaskStatus.PENDING.code(), GenerationTaskStatus.RUNNING.code(), owner, token, leaseSeconds) != 1)
            return;
        long started = System.nanoTime();
        log.info("大纲工作流已领取，taskId={}，owner={}", taskId, owner);
        ScheduledFuture<?> renewal = null;
        Exception executionFailure = null;
        try {
            renewal = heartbeat.scheduleWithFixedDelay(() -> {
                try {
                    if (tasks.renewGraph(taskId, token, leaseSeconds) != 1) {
                        log.warn("大纲任务续租未生效，taskId={}", taskId);
                    }
                } catch (RuntimeException exception) {
                    ErrorDiagnostics.error("大纲 Graph 租约续期失败", exception);
                }
            }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
            RunnableConfig config = RunnableConfig.builder().threadId(taskId)
                    .addMetadata(LEASE_TOKEN, token).addMetadata(OWNER, owner).build();
            Optional<Checkpoint> saved = checkpoints.get(config);
            log.info("大纲工作流执行位置，taskId={}，resume={}，nextNode={}", taskId,
                    saved.isPresent(), saved.map(Checkpoint::getNextNodeId).orElse(StateGraph.START));
            if (saved.isPresent()) {
                if (StateGraph.END.equals(saved.get().getNextNodeId())) {
                    // 等待独立分段租约时结束本次扫描，下次从数据库重新判断分段进度。
                    if (tasks.saveOutlineCheckpoint(taskId, token, null) != 1) {
                        throw new IllegalStateException("大纲 Graph 执行权已失效");
                    }
                } else {
                    config = config.withResume();
                }
            }
            graph.invoke(Map.of(), config).orElseThrow(() -> new IllegalStateException("大纲 Graph 未返回执行结果"));
        } catch (Exception exception) {
            executionFailure = exception;
            // 记录失败状态不能覆盖模型或 Graph 原始异常，数据库故障作为附加异常保留。
            try {
                if (batches.findByTask(taskId).stream().anyMatch(part -> part.getStatus() == GenerationBatchStatus.FAILED.code())) {
                    finalizer.failIfNeeded(taskId, token);
                } else {
                    boolean changed = sourceChanged(exception);
                    tasks.failGraph(taskId, token, GenerationTaskStatus.RUNNING.code(),
                            changed ? GenerationTaskStatus.SOURCE_CHANGED.code() : GenerationTaskStatus.PARTIAL_FAILED.code(),
                            changed ? "SOURCE_CHANGED" : OutlineFailureCode.GRAPH_FAILED.code());
                }
            } catch (RuntimeException persistenceFailure) {
                exception.addSuppressed(persistenceFailure);
            }
            log.warn("大纲工作流异常退出，taskId={}，exceptionType={}", taskId, exception.getClass().getName());
            throw exception;
        } finally {
            log.info("大纲工作流本次执行结束，taskId={}，failed={}，durationMs={}", taskId,
                    executionFailure != null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (renewal != null) renewal.cancel(false);
            // 清理失败不得覆盖执行失败；正常执行时清理异常仍需向调用方抛出。
            try {
                tasks.releaseGraph(taskId, token);
            } catch (RuntimeException releaseFailure) {
                if (executionFailure == null) throw releaseFailure;
                executionFailure.addSuppressed(releaseFailure);
            }
        }
    }

    /**
     * 关闭共享续租线程池；未完成任务会在租约失效后恢复。
     */
    @PreDestroy
    public void shutdown() {
        heartbeat.shutdown();
    }

    private void requireLease(String taskId, String token) {
        if (tasks.ownsGraph(taskId, token) != 1) throw new IllegalStateException("大纲 Graph 执行权已失效");
    }

    private void extract(String taskId, String owner) throws Exception {
        List<String> available = batches.runnable(taskId, GenerationBatchStatus.PENDING.code(), GenerationBatchStatus.RUNNING.code(), 1);
        if (!available.isEmpty()) worker.execute(available.get(0), owner);
    }

    private boolean sourceChanged(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException status) {
                int code = status.getStatusCode().value();
                if (code == 403 || code == 404 || code == 409) return true;
            }
        }
        return false;
    }

    private String route(String taskId) {
        List<AiGenerationBatchModel> parts = batches.findByTask(taskId);
        if (parts.stream().anyMatch(part -> part.getStatus() == GenerationBatchStatus.FAILED.code())) return FAIL;
        if (!batches.runnable(taskId, GenerationBatchStatus.PENDING.code(), GenerationBatchStatus.RUNNING.code(), 1).isEmpty())
            return EXTRACT;
        if (parts.stream().anyMatch(part -> part.getStatus() != GenerationBatchStatus.SUCCEEDED.code())) {
            log.info("大纲等待批次租约，本次执行退出，taskId={}", taskId);
            return WAIT;
        }
        return MERGE;
    }

    private OutlineMergeInput prepareMerge(AiGenerationTaskModel task) throws Exception {
        String taskId = task.getId();
        AdminMapper.UserRow user = users.user(task.getActorId());
        if (user == null || !user.enabled())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "任务发起用户已停用");
        outlines.checkGeneration(task.getMeetingId(), task.getActorId(), task.getTranscriptVersion(), task.getExpectedResultVersion());
        List<ChunkUtterance> all = new ArrayList<>();
        List<ChunkExtractionView> results = new ArrayList<>();
        for (AiGenerationBatchModel part : batches.findByTask(taskId)) {
            TranscriptChunkView chunk = json.readValue(part.getInputJson(), TranscriptChunkView.class);
            all.addAll(chunk.getUtterances());
            results.add(json.readValue(part.getOutputJson(), ChunkExtractionView.class));
        }
        OutlineMergeInput input = new OutlineMergeInput();
        input.setResults(results);
        input.setUtterances(all);
        return input;
    }

    private Map<String, Object> mergeStage(RunnableConfig execution, int round) throws Exception {
        String taskId = taskId(execution);
        String token = token(execution);
        requireLease(taskId, token);
        AiGenerationTaskModel task = tasks.findById(taskId);
        OutlineMergeInput input = prepareMerge(task);
        String hash = stageHash(round);
        // 业务结果可能先于 Graph 检查点提交，此时直接推进，避免重复模型调用。
        if (mergeStore.find(taskId, hash) == null) {
            OutlineTaskConfig config = json.readValue(task.getConfigJson(), OutlineTaskConfig.class);
            PromptVersion prompt = prompts.version(PromptScenarioCode.TRANSCRIPT_TOPIC_GROUPING.code(), config.getGroupingPromptVersionId());
            LinkedHashMap<String, TopicCandidate> candidates = topics.candidates(input.getResults());
            TopicAnalysisService.BatchCache cache = new TopicAnalysisService.BatchCache(mergeStore, taskId, token);
            OutlineGroupingProgress progress = round == 0
                    ? topics.groupInitial(candidates, prompt, task.getMeetingId(), task.getActorId(), taskId, cache)
                    : topics.groupAcross(candidates, stage(taskId, round - 1), round, prompt,
                    task.getMeetingId(), task.getActorId(), taskId, cache);
            mergeStore.save(taskId, token, hash, progress, progress.lastCallId());
        }
        return Map.of(ROUND, round);
    }

    private String mergeRoute(com.alibaba.cloud.ai.graph.OverAllState state, RunnableConfig config) throws Exception {
        int round = state.<Integer>value(ROUND).orElseThrow();
        return stage(taskId(config), round).needsCrossMerge() ? CROSS_MERGE : REVIEW;
    }

    private void saveDraft(RunnableConfig execution, int round) throws Exception {
        String taskId = taskId(execution);
        requireLease(taskId, token(execution));
        AiGenerationTaskModel task = tasks.findById(taskId);
        OutlineMergeInput input = prepareMerge(task);
        TranscriptOutlineDraft draft = generator.buildDraft(topics.toAnalysis(topics.candidates(input.getResults()),
                stage(taskId, round)), input.getUtterances(), task.getTranscriptVersion());
        OutlineTaskConfig config = json.readValue(task.getConfigJson(), OutlineTaskConfig.class);
        if (config.getReviewPromptVersionId() != null) {
            OutlineMergeResultModel reviewed = mergeStore.find(taskId, reviewHash(round));
            if (reviewed == null) throw new IllegalStateException("大纲审核结果不存在");
            draft.getSource().setReviewIssues(json.readValue(reviewed.getOutputJson(), OutlineReviewResult.class).issues());
        }
        finalizer.save(taskId, draft, token(execution));
    }

    private void reviewStage(RunnableConfig execution, int round) throws Exception {
        String taskId = taskId(execution);
        requireLease(taskId, token(execution));
        AiGenerationTaskModel task = tasks.findById(taskId);
        OutlineMergeInput input = prepareMerge(task);
        OutlineTaskConfig config = json.readValue(task.getConfigJson(), OutlineTaskConfig.class);
        // 已创建的历史任务没有审核提示词版本，继续原来的生成语义。
        if (config.getReviewPromptVersionId() == null) {
            log.info("历史任务跳过新增审核，taskId={}", taskId);
            return;
        }
        if (mergeStore.find(taskId, reviewHash(round)) != null) {
            log.info("复用大纲审核阶段，taskId={}，round={}", taskId, round);
            return;
        }
        PromptVersion prompt = prompts.version(PromptScenarioCode.TRANSCRIPT_OUTLINE_REVIEW.code(), config.getReviewPromptVersionId());
        OutlineReviewResult result = reviewer.review(task, token(execution), prompt, stage(taskId, round),
                topics.candidates(input.getResults()));
        mergeStore.save(taskId, token(execution), reviewHash(round), result, null);
    }

    private String reviewHash(int round) throws java.security.NoSuchAlgorithmException {
        return OutlineMergeStore.hash("outline-review-stage-v1:" + round);
    }

    private OutlineReviewResult reviewResult(String taskId, int round) throws Exception {
        OutlineMergeResultModel result = mergeStore.find(taskId, reviewHash(round));
        if (result == null) throw new IllegalStateException("大纲审核结果不存在");
        return json.readValue(result.getOutputJson(), OutlineReviewResult.class);
    }

    private String reviewRoute(RunnableConfig execution, int round) throws Exception {
        String taskId = taskId(execution);
        AiGenerationTaskModel task = tasks.findById(taskId);
        OutlineTaskConfig config = json.readValue(task.getConfigJson(), OutlineTaskConfig.class);
        if (config.getReviewPromptVersionId() == null) return SAVE;
        OutlineGroupingProgress progress = stage(taskId, round);
        int count = progress.revisionCount() == null ? 0 : progress.revisionCount();
        int limit = config.getMaxReviewRevisions() == null ? 0 : config.getMaxReviewRevisions();
        int issueCount = reviewResult(taskId, round).issues().size();
        String next = issueCount > 0 && count < limit ? REVISE : SAVE;
        log.info("大纲审核路由，taskId={}，round={}，issueCount={}，revisionCount={}，revisionLimit={}，nextNode={}",
                taskId, round, issueCount, count, limit, next);
        return next;
    }

    private Map<String, Object> reviseStage(RunnableConfig execution, int round) throws Exception {
        String taskId = taskId(execution);
        String token = token(execution);
        requireLease(taskId, token);
        AiGenerationTaskModel task = tasks.findById(taskId);
        OutlineMergeInput input = prepareMerge(task);
        int nextRound = round + 1;
        log.info("大纲修正阶段，taskId={}，sourceRound={}，targetRound={}", taskId, round, nextRound);
        // 修正完成而检查点尚未推进时，直接复用下一阶段，避免重复调用。
        if (mergeStore.find(taskId, stageHash(nextRound)) == null) {
            OutlineTaskConfig config = json.readValue(task.getConfigJson(), OutlineTaskConfig.class);
            PromptVersion prompt = prompts.version(PromptScenarioCode.TRANSCRIPT_TOPIC_GROUPING.code(),
                    config.getGroupingPromptVersionId());
            OutlineGroupingProgress revised = topics.revise(topics.candidates(input.getResults()), stage(taskId, round),
                    reviewResult(taskId, round), prompt, task.getMeetingId(), task.getActorId(), taskId,
                    new TopicAnalysisService.BatchCache(mergeStore, taskId, token));
            mergeStore.save(taskId, token, stageHash(nextRound), revised, revised.lastCallId());
        }
        return Map.of(ROUND, nextRound);
    }

    private OutlineGroupingProgress stage(String taskId, int round) throws Exception {
        OutlineMergeResultModel result = mergeStore.find(taskId, stageHash(round));
        if (result == null) throw new IllegalStateException("大纲归并阶段结果不存在");
        return json.readValue(result.getOutputJson(), OutlineGroupingProgress.class);
    }

    private String stageHash(int round) throws java.security.NoSuchAlgorithmException {
        // 任务固定分段、提示词版本，轮次在该任务的归并结果表内唯一。
        return OutlineMergeStore.hash("outline-stage-v1:" + round);
    }

    private static String taskId(RunnableConfig config) {
        return config.threadId().orElseThrow();
    }

    private static String token(RunnableConfig config) {
        return (String) config.metadata(LEASE_TOKEN).orElseThrow();
    }
}
