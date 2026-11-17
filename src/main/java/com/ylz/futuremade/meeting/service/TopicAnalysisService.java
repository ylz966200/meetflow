package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.model.dto.*;
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
        var candidates = candidates(chunks);
        var result = new TopicAnalysisResult();
        var snapshot = new OutlineSourceSnapshot();
        var groups = new ArrayList<OutlineSourceGroup>();
        var calls = new CallCounter();
        if (!candidates.isEmpty()) {
            var modelGroups = groupCandidates(candidates, prompt, meetingId, actor, requestKey, calls);
            var position = new HashMap<String, Integer>();
            int ordinal = 0;
            for (String id : candidates.keySet()) position.put(id, ordinal++);
            modelGroups.sort(Comparator.comparingInt(group -> group.getIds().stream().mapToInt(position::get).min().orElse(Integer.MAX_VALUE)));
            for (var modelGroup : modelGroups) {
                var group = new OutlineSourceGroup();
                group.setTitle(modelGroup.getTopic());
                group.setItemIds(modelGroup.getIds());
                groups.add(group);
            }
            result.setLastCallId(calls.lastCallId);
        }
        snapshot.setGroups(groups);
        snapshot.setItems(candidates);
        snapshot.setNeedsReview(true);
        snapshot.setPartialGrouping(calls.partialGrouping);
        result.setSource(snapshot);
        return result;
    }

    private LinkedHashMap<String, TopicCandidate> candidates(List<ChunkExtractionView> chunks) {
        var candidates = new LinkedHashMap<String, TopicCandidate>();
        for (var chunk : chunks) {
            for (int i = 0; i < chunk.getItems().size(); i++) {
                var item = chunk.getItems().get(i);
                var candidate = new TopicCandidate();
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

    private List<TopicGroupingGroup> groupCandidates(LinkedHashMap<String, TopicCandidate> candidates, PromptVersion prompt,
                                                     String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        List<TopicGroupingInput> values = new ArrayList<>(candidates.values());
        var batches = partition(values);
        var groups = new ArrayList<TopicGroupingGroup>();
        for (var batch : batches) groups.addAll(groupBatch(batch, prompt, meetingId, actor, requestKey, calls));
        if (batches.size() == 1) return groups;

        // 精简目录只用于判断主题，完整候选内容及来源仍保存在快照中。
        for (int round = 0; round < crossMergeRounds && groups.size() > 1; round++) {
            List<TopicGroupingInput> units = new ArrayList<>();
            var references = new LinkedHashMap<String, TopicGroupingGroup>();
            for (int i = 0; i < groups.size(); i++) {
                var group = groups.get(i);
                var unit = new TopicCatalogUnit();
                unit.setId("group-" + i);
                unit.setTopic(group.getTopic());
                var samples = new ArrayList<TopicSample>();
                samples.add(sample(candidates.get(group.getIds().get(0))));
                if (group.getIds().size() > 1)
                    samples.add(sample(candidates.get(group.getIds().get(group.getIds().size() - 1))));
                unit.setSamples(samples);
                units.add(unit);
                references.put(unit.getId(), group);
            }
            var groupedBatches = partition(units);
            var merged = new ArrayList<TopicGroupingGroup>();
            for (var batch : groupedBatches)
                merged.addAll(groupBatch(batch, prompt, meetingId, actor, requestKey, calls));
            var next = new ArrayList<TopicGroupingGroup>();
            for (var group : merged) {
                var expanded = new TopicGroupingGroup();
                expanded.setTopic(group.getTopic());
                var ids = new ArrayList<String>();
                for (String id : group.getIds()) ids.addAll(references.get(id).getIds());
                expanded.setIds(ids);
                next.add(expanded);
            }
            if (groupedBatches.size() == 1) return next;
            if (next.size() >= groups.size()) {
                calls.partialGrouping = true;
                return next;
            }
            groups = next;
        }
        if (groups.size() > 1) calls.partialGrouping = true;
        return groups;
    }

    private TopicSample sample(TopicCandidate candidate) {
        String content = candidate.getContent();
        int end = Math.min(content.length(), Math.max(0, crossSampleChars));
        if (end > 0 && end < content.length() && Character.isHighSurrogate(content.charAt(end - 1)) && Character.isLowSurrogate(content.charAt(end)))
            end--;
        var sample = new TopicSample();
        sample.setTopic(candidate.getTopic());
        sample.setContent(content.substring(0, end));
        return sample;
    }

    private List<TopicGroupingGroup> groupBatch(List<TopicGroupingInput> units, PromptVersion prompt,
                                                String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        String response;
        try {
            response = call(prompt, json.writeValueAsString(units), meetingId, actor, requestKey, calls);
        } catch (OutputTruncatedException ex) {
            if (units.size() < 2) throw ex;
            calls.partialGrouping = true;
            int middle = units.size() / 2;
            var result = new ArrayList<>(groupBatch(units.subList(0, middle), prompt, meetingId, actor, requestKey, calls));
            result.addAll(groupBatch(units.subList(middle, units.size()), prompt, meetingId, actor, requestKey, calls));
            return result;
        }
        var parsed = json.readValue(response, TopicGroupingResponse.class);
        if (parsed.getGroups() == null) throw new IllegalArgumentException("议题归并返回格式无效");
        Set<String> expected = new HashSet<>();
        for (var unit : units) expected.add(unit.getId());
        Set<String> seen = new HashSet<>();
        for (var group : parsed.getGroups()) {
            if (group == null || group.getTopic() == null || group.getTopic().isBlank() || group.getTopic().length() > 200 || group.getIds() == null || group.getIds().isEmpty())
                throw new IllegalArgumentException("议题归并结果无效");
            for (String id : group.getIds()) {
                if (id == null || !expected.contains(id) || !seen.add(id))
                    throw new IllegalArgumentException("议题归并包含重复或未知编号");
            }
        }
        if (!seen.equals(expected)) throw new IllegalArgumentException("议题归并遗漏了候选信息");
        return parsed.getGroups();
    }

    private List<List<TopicGroupingInput>> partition(List<TopicGroupingInput> values) throws Exception {
        var batches = new ArrayList<List<TopicGroupingInput>>();
        var batch = new ArrayList<TopicGroupingInput>();
        for (var value : values) {
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
        if (++calls.count > maxCalls) throw new IllegalStateException("分析调用次数达到上限，请缩小分析范围");
        long id = prompts.startCall(prompt, meetingId, null, actor, requestKey, null);
        calls.lastCallId = id;
        long start = System.nanoTime();
        try {
            prompts.recordInput(id, prompt.getContent(), input);
            var response = models.call(prompt.getContent(), input);
            String output = response.getResult().getOutput().getText();
            prompts.recordOutput(id, output);
            prompts.recordMetadata(id, response);
            if (response.hasFinishReasons(Set.of("length"))) throw new OutputTruncatedException();
            if (output == null || output.isBlank()) throw new IllegalStateException("模型返回空内容");
            prompts.finishCall(id, BusinessStatus.SUCCEEDED.code(), (System.nanoTime() - start) / 1_000_000, null);
            return output;
        } catch (Exception ex) {
            prompts.finishCall(id, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000,
                    ex instanceof OutputTruncatedException ? "OUTPUT_TRUNCATED" : "ANALYSIS_FAILED");
            throw ex;
        }
    }

    /**
     * 归并过程中的调用次数和完整性状态。
     */
    private static class CallCounter {
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
