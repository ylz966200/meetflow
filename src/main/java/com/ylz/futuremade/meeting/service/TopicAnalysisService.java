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
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 按需分批归并候选内容，生成大纲标题及来源编号。
 */
@Service
@Slf4j
public class TopicAnalysisService {
    private final AiModelGateway models;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final int maxBatchBytes;
    private final int maxCalls;
    private final int crossSampleChars;
    private final int crossMergeRounds;
    /** 单批格式或编号无效后的额外调用次数，仍受整次归并调用预算约束。 */
    @Value("${meetflow.ai.topic-validation-retries:2}")
    private int validationRetries = 2;

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

    /** 只重新归并审核涉及的主题，保留其他主题及全部候选编号。 */
    OutlineGroupingProgress revise(LinkedHashMap<String, TopicCandidate> candidates,
            OutlineGroupingProgress previous, OutlineReviewResult review, PromptVersion prompt,
            String meetingId, long actor, String requestKey, BatchCache cache) throws Exception {
        CallCounter calls = new CallCounter(cache);
        calls.count = previous.calls();
        calls.lastCallId = previous.lastCallId();
        Map<String, TopicGroupingGroup> topics = new HashMap<>();
        Map<String, Integer> sortOrders = new HashMap<>();
        Map<String, String> candidateTopics = new HashMap<>();
        for (int index = 0; index < previous.groups().size(); index++) {
            String topicId = "topic-" + index;
            TopicGroupingGroup group = previous.groups().get(index);
            topics.put(topicId, group);
            sortOrders.put(topicId, index);
            for (String id : group.getIds()) candidateTopics.put(id, topicId);
        }
        // 交叉建议组成连通范围，避免 A+B、B+C 重复消费旧 B 或丢失来源。
        List<Set<String>> scopes = new ArrayList<>();
        for (OutlineReviewIssue issue : review.issues()) {
            Set<String> scope = new HashSet<>();
            for (String id : issue.candidateIds()) {
                String topicId = candidateTopics.get(id);
                if (topicId == null) throw new IllegalArgumentException("审核引用了未知候选编号");
                scope.add(topicId);
            }
            Iterator<Set<String>> iterator = scopes.iterator();
            while (iterator.hasNext()) {
                Set<String> existing = iterator.next();
                if (!Collections.disjoint(existing, scope)) {
                    scope.addAll(existing);
                    iterator.remove();
                }
            }
            scopes.add(scope);
        }
        for (Set<String> scope : scopes) {
            List<String> orderedIds = scope.stream().sorted(Comparator.comparingInt(sortOrders::get)).toList();
            List<String> reasons = review.issues().stream()
                    .filter(issue -> issue.candidateIds().stream().anyMatch(id -> scope.contains(candidateTopics.get(id))))
                    .map(OutlineReviewIssue::reason).toList();
            calls.feedback = "依据审核意见修正本批议题标题或合并相近议题，不得拆分议题。"
                    + "只返回本批议题 id，禁止生成候选编号。审核意见：" + json.writeValueAsString(reasons);
            List<TopicGroupingInput> units = List.of();
            String input = "";
            // 保留全部议题及审核意见，仅逐级缩减辅助摘要；不把全部候选正文重新发送。
            for (int chars : new int[]{600, 300, 120, 30, 0}) {
                units = revisionUnits(orderedIds, topics, candidates, chars);
                input = json.writeValueAsString(new TopicGroupingCorrectionInput(calls.feedback, units));
                if (input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBatchBytes) break;
            }
            if (input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBatchBytes)
                throw new IllegalArgumentException("议题标题及审核意见超过修正预算，请提高预算或人工修改");
            log.info("局部议题修正输入已准备，taskId={}，topicCount={}，inputBytes={}，budgetBytes={}",
                    requestKey, units.size(), input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, maxBatchBytes);
            String hash = cache == null ? null : OutlineMergeStore.hash("outline-topic-revision-v1:" + prompt.getId() + ":" + input);
            OutlineMergeResultModel saved = cache == null ? null : cache.store().find(cache.taskId(), hash);
            List<TopicGroupingGroup> outputs;
            if (saved != null) {
                countCall(calls);
                calls.lastCallId = saved.getAiCallId();
                outputs = json.readValue(saved.getOutputJson(), TopicGroupingResponse.class).getGroups();
            } else {
                // 截断不能改为候选拆批，交给任务失败与重试机制处理。
                TopicGroupingResponse response = callValidated(units, prompt, input, meetingId, actor, requestKey, calls);
                outputs = response.getGroups();
                if (cache != null) cache.store().save(cache.taskId(), cache.token(), hash, response, calls.lastCallId);
            }
            for (TopicGroupingGroup output : outputs) {
                LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
                int position = Integer.MAX_VALUE;
                for (String topicId : output.getIds()) {
                    candidateIds.addAll(topics.get(topicId).getIds());
                    position = Math.min(position, sortOrders.get(topicId));
                }
                TopicGroupingGroup replacement = new TopicGroupingGroup();
                replacement.setTopic(output.getTopic());
                replacement.setIds(new ArrayList<>(candidateIds));
                String replacementId = "revised-" + position;
                topics.put(replacementId, replacement);
                sortOrders.put(replacementId, position);
            }
            for (String topicId : scope) topics.remove(topicId);
        }
        List<TopicGroupingGroup> revised = topics.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> sortOrders.get(entry.getKey())))
                .map(Map.Entry::getValue).toList();
        int count = previous.revisionCount() == null ? 0 : previous.revisionCount();
        return new OutlineGroupingProgress(revised, false, previous.partialGrouping(),
                calls.count, calls.lastCallId, count + 1);
    }

    /** 摘要只辅助语义判断，候选及来源始终从原议题继承，不受摘要截取影响。 */
    private List<TopicGroupingInput> revisionUnits(List<String> orderedIds,
            Map<String, TopicGroupingGroup> topics, Map<String, TopicCandidate> candidates, int maxChars) {
        List<TopicGroupingInput> units = new ArrayList<>();
        for (String topicId : orderedIds) {
            TopicGroupingGroup group = topics.get(topicId);
            TopicCatalogUnit unit = new TopicCatalogUnit();
            unit.setId(topicId);
            unit.setTopic(group.getTopic());
            StringBuilder summary = new StringBuilder();
            int perItem = Math.max(1, maxChars / group.getIds().size());
            for (String id : group.getIds()) {
                if (summary.length() >= maxChars) break;
                String text = candidates.get(id).getContent();
                int end = Math.min(text.length(), Math.min(perItem, maxChars - summary.length()));
                if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                summary.append(text, 0, end);
                if (summary.length() < maxChars) summary.append('；');
            }
            TopicSample sample = new TopicSample();
            sample.setTopic(group.getTopic());
            sample.setContent(summary.toString());
            unit.setSamples(maxChars == 0 ? List.of() : List.of(sample));
            units.add(unit);
        }
        return units;
    }

    private List<TopicGroupingGroup> groupBatch(List<TopicGroupingInput> units, PromptVersion prompt,
                                                String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        String input = calls.feedback == null ? json.writeValueAsString(units)
                : json.writeValueAsString(new TopicGroupingCorrectionInput(calls.feedback, units));
        BatchCache cache = calls.cache;
        String hash = cache == null ? null : OutlineMergeStore.hash("outline-batch-v1:" + prompt.getId() + ":" + input);
        OutlineMergeResultModel cached = cache == null ? null : cache.store().find(cache.taskId(), hash);
        if (cached != null) {
            log.info("归并复用已保存批次，taskId={}，candidateCount={}", requestKey, units.size());
            countCall(calls);
            calls.lastCallId = cached.getAiCallId();
            OutlineGroupingBatchResult saved = json.readValue(cached.getOutputJson(), OutlineGroupingBatchResult.class);
            return saved.splitRequired() ? splitBatch(units, prompt, meetingId, actor, requestKey, calls)
                    : saved.groups();
        }

        TopicGroupingResponse parsed;
        try {
            parsed = callValidated(units, prompt, input, meetingId, actor, requestKey, calls);
        } catch (OutputTruncatedException ex) {
            log.warn("归并输出截断，taskId={}，candidateCount={}", requestKey, units.size());
            if (units.size() < 2) throw ex;
            // 截断的内容不可用，只保存拆分决策，恢复时可以跳过已知会截断的调用。
            if (cache != null) {
                cache.store().save(cache.taskId(), cache.token(), hash,
                        new OutlineGroupingBatchResult(true, List.of()), calls.lastCallId);
            }
            return splitBatch(units, prompt, meetingId, actor, requestKey, calls);
        }
        if (cache != null) {
            cache.store().save(cache.taskId(), cache.token(), hash,
                    new OutlineGroupingBatchResult(false, parsed.getGroups()), calls.lastCallId);
        }
        return parsed.getGroups();
    }

    private TopicGroupingResponse callValidated(List<TopicGroupingInput> units, PromptVersion prompt, String input,
            String meetingId, long actor, String requestKey, CallCounter calls) throws Exception {
        String request = input;
        List<Exception> previousFailures = new ArrayList<>();
        for (int attempt = 0; ; attempt++) {
            String response = call(prompt, request, meetingId, actor, requestKey, calls);
            try {
                TopicGroupingResponse parsed = json.readValue(response, TopicGroupingResponse.class);
                validateGroups(units, parsed);
                return parsed;
            } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalidOutput) {
                // 仅恢复模型格式和编号错误；服务调用、权限和数据库异常继续向上抛出。
                if (attempt >= validationRetries) {
                    log.warn("归并纠错耗尽，taskId={}，attempts={}，candidateCount={}",
                            requestKey, attempt + 1, units.size());
                    previousFailures.forEach(invalidOutput::addSuppressed);
                    throw invalidOutput;
                }
                previousFailures.add(invalidOutput);
                log.warn("归并输出校验失败，准备纠错，taskId={}，retry={}，candidateCount={}，exceptionType={}",
                        requestKey, attempt + 1, units.size(), invalidOutput.getClass().getName());
                request = json.writeValueAsString(new TopicGroupingCorrectionInput(
                        "上次归并结果格式或编号校验失败，请重新生成完整 JSON。只使用 candidates 中的 id，"
                                + "每个 id 必须且只能出现在一个分组中，不得重复、遗漏或编造。"
                                + "返回 {groups:[{topic:主题名称,ids:[输入编号]}]}，字段名和字符串需使用 JSON 双引号。",
                        units));
                if (calls.feedback != null) {
                    request = json.writeValueAsString(new TopicGroupingCorrectionInput(
                            calls.feedback + "。上次输出校验失败，请严格保证所有输入编号恰好出现一次。", units));
                }
            }
        }
    }

    private void validateGroups(List<TopicGroupingInput> units, TopicGroupingResponse parsed) {
        if (parsed == null || parsed.getGroups() == null) throw new IllegalArgumentException("议题归并返回格式无效");
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
        log.info("归并模型调用开始，taskId={}，promptVersionId={}，callCount={}",
                requestKey, prompt.getId(), calls.count);
        AiCallContext audit = new AiCallContext(prompt, meetingId, null, actor, requestKey, null);
        try {
            org.springframework.ai.chat.model.ChatResponse response = models.call(audit, input);
            String output = response.getResult().getOutput().getText();
            if (response.hasFinishReasons(Set.of("length", "LENGTH"))) throw new OutputTruncatedException();
            if (output == null || output.isBlank()) throw new IllegalStateException("模型返回空内容");
            return output;
        } finally {
            calls.lastCallId = audit.getLastCallId();
            log.info("归并模型调用结束，taskId={}，aiCallId={}", requestKey, calls.lastCallId);
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
        /** 当前审核修正建议，普通归并时为空。 */
        private String feedback;
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
