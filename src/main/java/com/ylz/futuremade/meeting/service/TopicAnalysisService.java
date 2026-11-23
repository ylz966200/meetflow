package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.model.entity.OutlineMergeResultModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 按需分批归并候选内容，生成大纲标题及来源编号。
 */
@Service
public class TopicAnalysisService {
    private final AiModelGateway models;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final int maxBatchBytes;
    private final int maxCalls;
    private final int crossSampleChars;
    private final int crossMergeRounds;

    public TopicAnalysisService(AiModelGateway models, PromptService prompts, ObjectMapper json,
                                @Value("${meetflow.ai.topic-batch-max-bytes:24000}") int maxBatchBytes,
                                @Value("${meetflow.ai.topic-max-calls:80}") int maxCalls,
                                @Value("${meetflow.ai.topic-cross-sample-chars:120}") int crossSampleChars,
                                @Value("${meetflow.ai.topic-cross-merge-rounds:4}") int crossMergeRounds) {
        this.models = models;
        this.prompts = prompts;
        this.json = json;
        this.maxBatchBytes = maxBatchBytes;
        this.maxCalls = maxCalls;
        this.crossSampleChars = crossSampleChars;
        this.crossMergeRounds = crossMergeRounds;
    }

    /**
     * 保留全部候选内容及来源，每次模型调用关联同一个请求。
     */
    public TopicAnalysisResult group(List<ChunkExtractionView> chunks, String meetingId, long actor, String requestKey) throws Exception {
        return group(chunks, meetingId, actor, requestKey, prompts.active(PromptScenarioCode.TRANSCRIPT_TOPIC_GROUPING.code()));
    }

    /**
     * 使用固定提示词版本归并候选内容，供后台任务恢复使用。
     */
    public TopicAnalysisResult group(List<ChunkExtractionView> chunks, String meetingId, long actor, String requestKey, PromptVersion prompt) throws Exception {
        LinkedHashMap<String, TopicCandidate> candidates = candidates(chunks);
        OutlineGroupingProgress progress = groupInitial(candidates, prompt, meetingId, actor, requestKey, null);
        for (int round = 1; progress.needsCrossMerge(); round++) {
            progress = groupAcross(candidates, progress, round, prompt, meetingId, actor, requestKey, null);
        }
        return toAnalysis(candidates, progress);
    }

    TopicAnalysisResult toAnalysis(LinkedHashMap<String, TopicCandidate> candidates, OutlineGroupingProgress progress) {
        TopicAnalysisResult result = new TopicAnalysisResult();
        OutlineSourceSnapshot snapshot = new OutlineSourceSnapshot();
        List<OutlineSourceGroup> groups = new ArrayList<>();
        Map<String, Integer> position = new HashMap<>();
        int index = 0;
        for (String id : candidates.keySet()) position.put(id, index++);
        List<TopicGroupingGroup> ordered = new ArrayList<>(progress.groups());
        ordered.sort(Comparator.comparingInt(group ->
                group.getIds().stream().mapToInt(position::get).min().orElseThrow()));
        for (TopicGroupingGroup modelGroup : ordered) {
            OutlineSourceGroup group = new OutlineSourceGroup();
            group.setTitle(modelGroup.getTopic());
            group.setItemIds(modelGroup.getIds());
            groups.add(group);
        }
        result.setLastCallId(progress.lastCallId());
        snapshot.setGroups(groups);
        snapshot.setItems(candidates);
        snapshot.setNeedsReview(true);
        snapshot.setPartialGrouping(progress.partialGrouping());
        result.setSource(snapshot);
        return result;
    }

    LinkedHashMap<String, TopicCandidate> candidates(List<ChunkExtractionView> chunks) {
        LinkedHashMap<String, TopicCandidate> candidates = new LinkedHashMap<>();
        for (ChunkExtractionView chunk : chunks) {
            for (int i = 0; i < chunk.getItems().size(); i++) {
                ChunkExtractionItem item = chunk.getItems().get(i);
                TopicCandidate candidate = new TopicCandidate();
                candidate.setId(chunk.getChunkIndex() + ":" + i);
                candidate.setChunkIndex(chunk.getChunkIndex());
                candidate.setTopic(item.getTopic());
                candidate.setType(item.getType());
                candidate.setContent(item.getContent());
                candidate.setEvidenceIndices(item.getEvidenceIndices());
                candidate.setNeedsReview(item.getNeedsReview());
                candidates.put(candidate.getId(), candidate);
            }
        }
        return candidates;
    }

    /** 首批归并保留每个模型批次的完整候选覆盖。 */
    OutlineGroupingProgress groupInitial(LinkedHashMap<String, TopicCandidate> candidates, PromptVersion prompt,
            String meetingId, long actor, String requestKey, BatchCache cache) throws Exception {
        List<List<TopicGroupingInput>> batches = partition(new ArrayList<>(candidates.values()));
        CallCounter calls = new CallCounter(cache);
        List<TopicGroupingGroup> groups = new ArrayList<>();
        for (List<TopicGroupingInput> batch : batches) {
            groups.addAll(groupBatch(batch, prompt, meetingId, actor, requestKey, calls));
        }
        boolean needsCrossMerge = (batches.size() > 1 || calls.partialGrouping) && groups.size() > 1;
        return new OutlineGroupingProgress(groups, needsCrossMerge, calls.partialGrouping, calls.count, calls.lastCallId);
    }

    /** 跨批归并只发送精简目录，结果仍关联原候选编号。 */
    OutlineGroupingProgress groupAcross(LinkedHashMap<String, TopicCandidate> candidates,
            OutlineGroupingProgress previous, int round, PromptVersion prompt, String meetingId,
            long actor, String requestKey, BatchCache cache) throws Exception {
        CallCounter calls = new CallCounter(cache);
        calls.count = previous.calls();
        calls.lastCallId = previous.lastCallId();
        List<TopicGroupingGroup> groups = previous.groups();
        List<TopicGroupingInput> units = new ArrayList<>();
        Map<String, TopicGroupingGroup> references = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            TopicGroupingGroup group = groups.get(i);
            TopicCatalogUnit unit = new TopicCatalogUnit();
            unit.setId("group-" + i);
            unit.setTopic(group.getTopic());
            List<TopicSample> samples = new ArrayList<>();
            samples.add(sample(candidates.get(group.getIds().get(0))));
            if (group.getIds().size() > 1)
                samples.add(sample(candidates.get(group.getIds().get(group.getIds().size() - 1))));
            unit.setSamples(samples);
            units.add(unit);
            references.put(unit.getId(), group);
        }
        List<List<TopicGroupingInput>> groupedBatches = partition(units);
        List<TopicGroupingGroup> merged = new ArrayList<>();
        for (List<TopicGroupingInput> batch : groupedBatches)
            merged.addAll(groupBatch(batch, prompt, meetingId, actor, requestKey, calls));
        List<TopicGroupingGroup> next = new ArrayList<>();
        for (TopicGroupingGroup group : merged) {
            TopicGroupingGroup expanded = new TopicGroupingGroup();
            expanded.setTopic(group.getTopic());
            List<String> ids = new ArrayList<>();
            for (String id : group.getIds()) ids.addAll(references.get(id).getIds());
            expanded.setIds(ids);
            next.add(expanded);
        }
        boolean comparedTogether = groupedBatches.size() == 1 && !calls.partialGrouping;
        boolean needsCrossMerge = !comparedTogether && next.size() > 1
                && next.size() < groups.size() && round < crossMergeRounds;
        boolean partial = !comparedTogether && next.size() > 1 && !needsCrossMerge;
        return new OutlineGroupingProgress(next, needsCrossMerge, partial, calls.count, calls.lastCallId);
    }

    private TopicSample sample(TopicCandidate candidate) {
        String content = candidate.getContent();
        int end = Math.min(content.length(), Math.max(0, crossSampleChars));
        if (end > 0 && end < content.length() && Character.isHighSurrogate(content.charAt(end - 1)) && Character.isLowSurrogate(content.charAt(end)))
            end--;
        TopicSample sample = new TopicSample();
        sample.setTopic(candidate.getTopic());
        sample.setContent(content.substring(0, end));
        return sample;
    }

    private List<TopicGroupingGroup> groupBatch(List<TopicGroupingInput> units, PromptVersion prompt,
                                                String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        String input = json.writeValueAsString(units);
        BatchCache cache = calls.cache;
        String hash = cache == null ? null : OutlineMergeStore.hash("outline-batch-v1:" + prompt.getId() + ":" + input);
        OutlineMergeResultModel cached = cache == null ? null : cache.store().find(cache.taskId(), hash);
        if (cached != null) {
            countCall(calls);
            calls.lastCallId = cached.getAiCallId();
            OutlineGroupingBatchResult saved = json.readValue(cached.getOutputJson(), OutlineGroupingBatchResult.class);
            return saved.splitRequired() ? splitBatch(units, prompt, meetingId, actor, requestKey, calls)
                    : saved.groups();
        }

        String response;
        try {
            response = call(prompt, input, meetingId, actor, requestKey, calls);
        } catch (OutputTruncatedException ex) {
            if (units.size() < 2) throw ex;
            // 截断的内容不可用，只保存拆分决策，恢复时可以跳过已知会截断的调用。
            if (cache != null) {
                cache.store().save(cache.taskId(), cache.token(), hash,
                        new OutlineGroupingBatchResult(true, List.of()), calls.lastCallId);
            }
            return splitBatch(units, prompt, meetingId, actor, requestKey, calls);
        }
        TopicGroupingResponse parsed = json.readValue(response, TopicGroupingResponse.class);
        if (parsed.getGroups() == null) throw new IllegalArgumentException("议题归并返回格式无效");
        Set<String> expected = new HashSet<>();
        for (TopicGroupingInput unit : units) expected.add(unit.getId());
        Set<String> seen = new HashSet<>();
        for (TopicGroupingGroup group : parsed.getGroups()) {
            if (group == null || group.getTopic() == null || group.getTopic().isBlank() || group.getTopic().length() > 200 || group.getIds() == null || group.getIds().isEmpty())
                throw new IllegalArgumentException("议题归并结果无效");
            for (String id : group.getIds()) {
                if (id == null || !expected.contains(id) || !seen.add(id))
                    throw new IllegalArgumentException("议题归并包含重复或未知编号");
            }
        }
        if (!seen.equals(expected)) throw new IllegalArgumentException("议题归并遗漏了候选信息");
        if (cache != null) {
            cache.store().save(cache.taskId(), cache.token(), hash,
                    new OutlineGroupingBatchResult(false, parsed.getGroups()), calls.lastCallId);
        }
        return parsed.getGroups();
    }

    private List<TopicGroupingGroup> splitBatch(List<TopicGroupingInput> units, PromptVersion prompt,
            String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        calls.partialGrouping = true;
        int middle = units.size() / 2;
        List<TopicGroupingGroup> groups = new ArrayList<>(
                groupBatch(units.subList(0, middle), prompt, meetingId, actor, requestKey, calls));
        groups.addAll(groupBatch(units.subList(middle, units.size()), prompt, meetingId, actor, requestKey, calls));
        return groups;
    }

    private List<List<TopicGroupingInput>> partition(List<TopicGroupingInput> values) throws Exception {
        List<List<TopicGroupingInput>> batches = new ArrayList<>();
        List<TopicGroupingInput> batch = new ArrayList<>();
        for (TopicGroupingInput value : values) {
            batch.add(value);
            if (json.writeValueAsBytes(batch).length <= maxBatchBytes) continue;
            batch.remove(batch.size() - 1);
            if (batch.isEmpty()) throw new IllegalArgumentException("单条候选信息超过分析预算");
            batches.add(List.copyOf(batch));
            batch.clear();
            batch.add(value);
            if (json.writeValueAsBytes(batch).length > maxBatchBytes)
                throw new IllegalArgumentException("单条候选信息超过分析预算");
        }
        if (!batch.isEmpty()) batches.add(List.copyOf(batch));
        return batches;
    }

    private String call(PromptVersion prompt, String input, String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        countCall(calls);
        AiCallContext audit = new AiCallContext(prompt, meetingId, null, actor, requestKey, null);
        try {
            org.springframework.ai.chat.model.ChatResponse response = models.call(audit, input);
            String output = response.getResult().getOutput().getText();
            if (response.hasFinishReasons(Set.of("length", "LENGTH"))) throw new OutputTruncatedException();
            if (output == null || output.isBlank()) throw new IllegalStateException("模型返回空内容");
            return output;
        } finally {
            calls.lastCallId = audit.getLastCallId();
        }
    }

    private void countCall(CallCounter calls) {
        if (++calls.count > maxCalls) throw new IllegalStateException("分析调用次数达到上限，请缩小分析范围");
    }

    /**
     * 本次后台归并使用的业务持久化上下文。
     * @param store 归并结果存储
     * @param taskId 大纲生成任务 ID
     * @param token 当前任务执行令牌，只在内存中传递
     */
    record BatchCache(OutlineMergeStore store, String taskId, String token) {}

    /**
     * 归并过程中的调用次数和完整性状态。
     */
    private static class CallCounter {
        /** 后台任务结果缓存，同步生成时为空。 */
        private final BatchCache cache;

        private CallCounter(BatchCache cache) { this.cache = cache; }

        /**
         * 已调用次数。
         */
        private int count;
        /**
         * 是否需要人工检查跨批归并。
         */
        private boolean partialGrouping;
        /**
         * 最后一次调用记录 ID。
         */
        private Long lastCallId;
    }

    /**
     * 模型输出被截断，可拆分输入重试。
     */
    private static class OutputTruncatedException extends RuntimeException {
        OutputTruncatedException() {
            super("模型输出被截断，请缩小分析范围或调整输出上限");
        }
    }
}
