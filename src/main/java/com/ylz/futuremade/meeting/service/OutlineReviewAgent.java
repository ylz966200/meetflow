package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.assistant.service.ModelOutputTruncatedException;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.model.entity.*;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;

import java.util.*;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 新协议一次审核完整大纲标题和摘要，直接返回修正意见；旧任务保留其固定版本的审核语义。
 */
@Service
@Slf4j
public class OutlineReviewAgent {
    private final AiModelGateway models;
    private final OutlineMergeStore store;
    private final ObjectMapper json;
    private final int maxBytes;

    public OutlineReviewAgent(AiModelGateway models, OutlineMergeStore store, ObjectMapper json,
                              @Value("${meetflow.outline.review-max-bytes:24000}") int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("审核输入预算必须大于零");
        this.models = models;
        this.store = store;
        this.json = json;
        this.maxBytes = maxBytes;
    }

    /**
     * 第三版起每轮只审核完整议题摘要；旧版任务继续使用原有候选审核流程。
     */
    public OutlineReviewResult review(AiGenerationTaskModel task, String token, PromptVersion prompt,
                                      OutlineGroupingProgress grouping, Map<String, TopicCandidate> candidates) throws Exception {
        // 新版本使用议题协议；已创建的旧任务继续使用其固定提示词对应的输入格式。
        if (prompt.getVersion() != null && prompt.getVersion() >= 3) {
            return reviewTopics(task, token, prompt, grouping, candidates);
        }
        List<OutlineReviewIssue> issues = new ArrayList<>();
        if (grouping.groups().isEmpty()) return new OutlineReviewResult(List.of());
        List<TopicCandidate> ordered = new ArrayList<>();
        for (TopicGroupingGroup group : grouping.groups()) {
            for (String id : group.getIds()) ordered.add(candidates.get(id));
        }
        String title = grouping.groups().size() == 1 ? grouping.groups().get(0).getTopic() : "最终会议大纲";
        OutlineReviewInput complete = new OutlineReviewInput(title, ordered, grouping.groups(), false);
        if (json.writeValueAsBytes(complete).length <= maxBytes) {
            try {
                log.info("使用完整大纲整体审核，taskId={}，groupCount={}，candidateCount={}",
                        task.getId(), grouping.groups().size(), ordered.size());
                return reviewBatch(task, token, prompt, complete);
            } catch (ModelOutputTruncatedException truncated) {
                // 整体响应截断时转摘要流程，避免拆批后失去跨主题比较范围。
                log.warn("完整审核输出截断，转整体摘要审核，taskId={}", task.getId());
            }
        }
        OutlineReviewInput summary = summaryInput(title, ordered, grouping.groups());
        log.info("使用完整目录摘要审核，taskId={}，groupCount={}，candidateCount={}",
                task.getId(), grouping.groups().size(), ordered.size());
        OutlineReviewResult global = reviewBatch(task, token, prompt, summary);
        issues.addAll(global.issues());
        Set<String> questioned = new HashSet<>();
        for (OutlineReviewIssue issue : global.issues()) questioned.addAll(issue.candidateIds());
        return reviewDetails(task, token, prompt, grouping, candidates, issues, questioned);
    }

    private OutlineReviewResult reviewDetails(AiGenerationTaskModel task, String token, PromptVersion prompt,
                                              OutlineGroupingProgress grouping, Map<String, TopicCandidate> candidates,
                                              List<OutlineReviewIssue> issues, Set<String> questioned) throws Exception {
        for (TopicGroupingGroup group : grouping.groups()) {
            if (group.getIds().stream().noneMatch(questioned::contains)) continue;
            List<TopicCandidate> batch = new ArrayList<>();
            for (String id : group.getIds()) {
                TopicCandidate candidate = candidates.get(id);
                batch.add(candidate);
                if (json.writeValueAsBytes(new OutlineReviewInput(group.getTopic(), batch)).length <= maxBytes)
                    continue;
                batch.remove(batch.size() - 1);
                if (!batch.isEmpty()) issues.addAll(reviewBatch(task, token, prompt,
                        new OutlineReviewInput(group.getTopic(), List.copyOf(batch))).issues());
                batch.clear();
                batch.add(candidate);
                if (json.writeValueAsBytes(new OutlineReviewInput(group.getTopic(), batch)).length > maxBytes) {
                    throw new IllegalArgumentException("单条候选超过审核预算");
                }
            }
            if (!batch.isEmpty()) issues.addAll(reviewBatch(task, token, prompt,
                    new OutlineReviewInput(group.getTopic(), List.copyOf(batch))).issues());
        }
        log.info("大纲审核完成，taskId={}，groupCount={}，issueCount={}",
                task.getId(), grouping.groups().size(), issues.size());
        return new OutlineReviewResult(issues.stream().distinct().toList());
    }

    private OutlineReviewResult reviewTopics(AiGenerationTaskModel task, String token, PromptVersion prompt,
                                             OutlineGroupingProgress grouping, Map<String, TopicCandidate> candidates) throws Exception {
        if (grouping.groups().isEmpty()) return new OutlineReviewResult(List.of());
        OutlineTopicReviewRequest request = topicRequest(grouping.groups(), candidates);
        String serialized = json.writeValueAsString(request);
        String hash = OutlineMergeStore.hash("outline-topic-review-v3:" + prompt.getId() + ":" + serialized);
        OutlineMergeResultModel cached = store.find(task.getId(), hash);
        OutlineTopicReviewResponse result;
        if (cached != null) {
            result = json.readValue(cached.getOutputJson(), OutlineTopicReviewResponse.class);
        } else {
            log.info("整体议题摘要审核开始，taskId={}，topicCount={}", task.getId(), request.topics().size());
            AiCallContext audit = new AiCallContext(prompt, task.getMeetingId(), task.getTranscriptVersion(),
                    task.getActorId(), task.getId(), null);
            ChatResponse response = models.call(task.getModelName(), audit, serialized);
            if (response.hasFinishReasons(Set.of("length", "LENGTH"))) throw new ModelOutputTruncatedException();
            result = json.readValue(response.getResult().getOutput().getText(), OutlineTopicReviewResponse.class);
            validateTopics(result, grouping.groups().size());
            store.save(task.getId(), token, hash, result, audit.getLastCallId());
        }
        List<OutlineReviewIssue> issues = new ArrayList<>();
        for (OutlineTopicReviewIssue issue : result.issues()) {
            Set<String> ids = new LinkedHashSet<>();
            for (Integer topicId : issue.topicIds()) ids.addAll(grouping.groups().get(topicId - 1).getIds());
            issues.add(new OutlineReviewIssue(List.copyOf(ids), issue.reason()));
        }
        log.info("整体议题审核结束，直接返回修正意见，taskId={}，topicCount={}，issueCount={}",
                task.getId(), request.topics().size(), issues.size());
        return new OutlineReviewResult(issues);
    }

    private OutlineTopicReviewRequest topicRequest(List<TopicGroupingGroup> groups,
                                                   Map<String, TopicCandidate> candidates) throws Exception {
        for (int limit : new int[]{600, 300, 120, 30}) {
            List<OutlineTopicReviewItem> topics = new ArrayList<>();
            for (int index = 0; index < groups.size(); index++) {
                TopicGroupingGroup group = groups.get(index);
                StringBuilder content = new StringBuilder();
                // 均匀分配摘要长度，保留每条候选的一部分；不把候选编号发送给整体审核。
                int perItem = Math.max(1, limit / group.getIds().size());
                for (String id : group.getIds()) {
                    if (content.length() >= limit) break;
                    String text = candidates.get(id).getContent();
                    int end = Math.min(text.length(), Math.min(perItem, limit - content.length()));
                    if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                    content.append(text, 0, end);
                    if (content.length() < limit) content.append('；');
                }
                topics.add(new OutlineTopicReviewItem(index + 1, group.getTopic(), content.toString()));
            }
            OutlineTopicReviewRequest request = new OutlineTopicReviewRequest(topics);
            if (json.writeValueAsBytes(request).length <= maxBytes) return request;
        }
        throw new IllegalArgumentException("议题标题和摘要超过整体审核预算，请提高审核预算");
    }

    private void validateTopics(OutlineTopicReviewResponse result, int count) {
        if (result == null || result.issues() == null || result.issues().size() > count) {
            throw new IllegalArgumentException("整体议题审核返回格式无效");
        }
        for (OutlineTopicReviewIssue issue : result.issues()) {
            if (issue == null || issue.reason() == null || issue.reason().isBlank() || issue.reason().length() > 1000
                    || issue.topicIds() == null || issue.topicIds().isEmpty()
                    || issue.topicIds().stream().anyMatch(id -> id == null || id < 1 || id > count)
                    || new HashSet<>(issue.topicIds()).size() != issue.topicIds().size()) {
                throw new IllegalArgumentException("整体审核议题编号或原因无效");
            }
        }
    }

    private OutlineReviewInput summaryInput(String title, List<TopicCandidate> ordered,
                                            List<TopicGroupingGroup> groups) throws Exception {
        for (int chars : new int[]{120, 30, 0}) {
            List<TopicCandidate> summaries = new ArrayList<>();
            for (TopicCandidate candidate : ordered) {
                TopicCandidate summary = new TopicCandidate();
                summary.setId(candidate.getId());
                summary.setTopic(candidate.getTopic());
                String content = candidate.getContent();
                int end = Math.min(chars, content.length());
                if (end > 0 && end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) end--;
                summary.setContent(content.substring(0, end));
                summaries.add(summary);
            }
            OutlineReviewInput input = new OutlineReviewInput(title, summaries, groups, true);
            if (json.writeValueAsBytes(input).length <= maxBytes) return input;
        }
        throw new IllegalArgumentException("完整大纲目录超过审核预算，请提高审核预算或减少大纲规模");
    }

    private OutlineReviewResult reviewBatch(AiGenerationTaskModel task, String token, PromptVersion prompt,
                                            OutlineReviewInput input) throws Exception {
        String serialized = json.writeValueAsString(input);
        String hash = OutlineMergeStore.hash("outline-review-v2:" + prompt.getId() + ":" + serialized);
        OutlineMergeResultModel cached = store.find(task.getId(), hash);
        if (cached != null) {
            log.info("审核复用已保存批次，taskId={}，candidateCount={}", task.getId(), input.candidates().size());
            return json.readValue(cached.getOutputJson(), OutlineReviewResult.class);
        }
        AiCallContext audit = new AiCallContext(prompt, task.getMeetingId(), task.getTranscriptVersion(),
                task.getActorId(), task.getId(), null);
        log.info("审核模型调用开始，taskId={}，candidateCount={}，promptVersionId={}",
                task.getId(), input.candidates().size(), prompt.getId());
        ChatResponse response = models.call(task.getModelName(), audit, serialized);
        log.info("审核模型调用返回，taskId={}，aiCallId={}", task.getId(), audit.getLastCallId());
        if (response.hasFinishReasons(Set.of("length", "LENGTH"))) {
            if (!input.groups().isEmpty()) throw new ModelOutputTruncatedException();
            if (input.candidates().size() < 2) throw new ModelOutputTruncatedException();
            int middle = input.candidates().size() / 2;
            log.warn("审核输出截断，拆分重试，taskId={}，candidateCount={}", task.getId(), input.candidates().size());
            // 截断后缩小审核范围，成功子批独立保存，保留原候选编号。
            List<OutlineReviewIssue> issues = new ArrayList<>(reviewBatch(task, token, prompt,
                    new OutlineReviewInput(input.title(), input.candidates().subList(0, middle))).issues());
            issues.addAll(reviewBatch(task, token, prompt,
                    new OutlineReviewInput(input.title(), input.candidates().subList(middle, input.candidates().size()))).issues());
            OutlineReviewResult combined = new OutlineReviewResult(issues);
            store.save(task.getId(), token, hash, combined, audit.getLastCallId());
            return combined;
        }
        OutlineReviewResult result = json.readValue(response.getResult().getOutput().getText(), OutlineReviewResult.class);
        validate(result, input);
        log.info("审核批次校验通过，taskId={}，issueCount={}，aiCallId={}",
                task.getId(), result.issues().size(), audit.getLastCallId());
        store.save(task.getId(), token, hash, result, audit.getLastCallId());
        return result;
    }

    private void validate(OutlineReviewResult result, OutlineReviewInput input) {
        if (result == null || result.issues() == null || result.issues().size() > input.candidates().size()) {
            throw new IllegalArgumentException("大纲审核返回格式无效");
        }
        Set<String> allowed = new HashSet<>();
        for (TopicCandidate candidate : input.candidates()) allowed.add(candidate.getId());
        for (OutlineReviewIssue issue : result.issues()) {
            if (issue == null || issue.reason() == null || issue.reason().isBlank() || issue.reason().length() > 1000
                    || issue.candidateIds() == null || issue.candidateIds().isEmpty()
                    || !allowed.containsAll(issue.candidateIds())
                    || new HashSet<>(issue.candidateIds()).size() != issue.candidateIds().size()) {
                throw new IllegalArgumentException("大纲审核问题或来源编号无效");
            }
        }
    }
}
