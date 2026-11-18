
package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.ModelOutputTruncatedException;
import com.ylz.futuremade.assistant.service.ToolCallingExecutor;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * 基于确认大纲的来源快照分批生成纪要和行动项。
 */
@Slf4j
@Service
public class MinutesBatchGenerator {
    private final ToolCallingExecutor executor;
    private final MeetingService meetings;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final int maxBatchBytes;
    private final int maxToolCalls;
    private final int maxSourceSegments;
    private final int maxSourceChars;

    public MinutesBatchGenerator(ToolCallingExecutor executor, MeetingService meetings, PromptService prompts,
                                 ObjectMapper json, @Value("${meetflow.minutes.batch-max-bytes:24000}") int maxBatchBytes,
                                 @Value("${meetflow.minutes.max-tool-calls:4}") int maxToolCalls,
                                 @Value("${meetflow.minutes.max-source-segments:20}") int maxSourceSegments,
                                 @Value("${meetflow.minutes.max-source-chars:6000}") int maxSourceChars) {
        this.executor = executor;
        this.meetings = meetings;
        this.prompts = prompts;
        this.json = json;
        this.maxBatchBytes = maxBatchBytes;
        this.maxToolCalls = maxToolCalls;
        this.maxSourceSegments = maxSourceSegments;
        this.maxSourceChars = maxSourceChars;
    }

    public String generate(OutlineView outline, PromptVersion prompt, String meetingId, long actor, String username, String key) throws Exception {
        return MinutesStructure.format(generateStructured(outline, prompt, meetingId, actor, username, key));
    }

    /**
     * 生成结构化纪要，保留每条纪要、行动项和来源的关系。
     */
    public StructuredMinutes generateStructured(OutlineView outline, PromptVersion prompt, String meetingId, long actor, String username, String key) throws Exception {
        var source = outline.getSources();
        var batches = prepareBatches(outline);
        List<MinutesBatchItem> minutes = new ArrayList<>();
        List<MinutesActionItem> actions = new ArrayList<>();
        for (var batch : batches)
            generateBatch(batch, source, prompt, meetingId, outline, actor, username, key, minutes, actions);
        minutes.sort(Comparator.comparing(MinutesBatchItem::getOutlineId));
        return MinutesStructure.fromBatches(outline, minutes, actions);
    }

    /**
     * 按大纲顺序整理批次结果，供同步及后台流程复用。
     */
    public String formatResult(OutlineView outline, List<MinutesBatchItem> minutes, List<MinutesActionItem> actions) {
        String result = MinutesStructure.format(MinutesStructure.fromBatches(outline, minutes, actions));
        if (result.length() > MinutesService.MAX_CONTENT_LENGTH) {
            log.warn("生成结果超过纪要长度上限（{} 个 UTF-16 单元）", MinutesService.MAX_CONTENT_LENGTH);
        }
        return result;
    }

    /**
     * 返回当前分批和工具配置，用于创建不可变任务快照。
     */
    public MinutesTaskConfig taskConfig() {
        var config = new MinutesTaskConfig();
        config.setBatchMaxBytes(maxBatchBytes);
        config.setMaxToolCalls(maxToolCalls);
        config.setMaxSourceSegments(maxSourceSegments);
        config.setMaxSourceChars(maxSourceChars);
        return config;
    }

    /**
     * 校验来源并按固定预算生成批次，供同步和后台任务共用。
     */
    public List<List<MinutesOutlineInput>> prepareBatches(OutlineView outline) throws Exception {
        var source = outline.getSources();
        if (source == null || source.getGroups() == null || source.getSourceSegments() == null
                || source.getItems() == null || source.getGroups().size() != outline.getItems().size()
                || !Objects.equals(source.getTranscriptVersion(), outline.getTranscriptVersion()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲缺少可用的文字稿来源，请重新生成并确认大纲");
        var inputs = new ArrayList<MinutesOutlineInput>();
        for (int i = 0; i < outline.getItems().size(); i++) {
            var group = source.getGroups().get(i);
            var candidates = new ArrayList<TopicCandidate>();
            for (String id : group.getItemIds()) {
                var candidate = source.getItems().get(id);
                if (candidate == null || candidate.getEvidenceIndices() == null || candidate.getEvidenceIndices().isEmpty())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲来源不完整，请检查后重新确认");
                candidates.add(candidate);
            }
            if (candidates.isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第 " + (i + 1) + " 条大纲没有关联内容，请补充来源后再生成纪要");
            var input = new MinutesOutlineInput();
            input.setOutlineId(i + 1);
            input.setTitle(outline.getItems().get(i));
            input.setCandidates(candidates);
            inputs.add(input);
        }
        List<List<MinutesOutlineInput>> batches = new ArrayList<>();
        List<MinutesOutlineInput> current = new ArrayList<>();
        int bytes = 2;
        for (var input : inputs) {
            int size = json.writeValueAsBytes(input).length + 1;
            if (size > maxBatchBytes)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第 " + input.getOutlineId() + " 条大纲关联内容过长，请先调整大纲来源");
            if (!current.isEmpty() && bytes + size > maxBatchBytes) {
                batches.add(current);
                current = new ArrayList<>();
                bytes = 2;
            }
            current.add(input);
            bytes += size;
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    private void generateBatch(List<MinutesOutlineInput> batch, OutlineSourceSnapshot source, PromptVersion prompt, String meetingId,
                               OutlineView outline, long actor, String username, String key, List<MinutesBatchItem> minutes, List<MinutesActionItem> actions) throws Exception {
        try {
            var result = generateSingleBatch(batch, source, prompt, meetingId, outline, actor, username, key, taskConfig()).getResult();
            minutes.addAll(result.getMinutes().stream().sorted(Comparator.comparing(MinutesBatchItem::getOutlineId)).toList());
            actions.addAll(result.getActions());
        } catch (ModelOutputTruncatedException ex) {
            if (batch.size() < 2) throw ex;
            int middle = batch.size() / 2;
            generateBatch(batch.subList(0, middle), source, prompt, meetingId, outline, actor, username, key, minutes, actions);
            generateBatch(batch.subList(middle, batch.size()), source, prompt, meetingId, outline, actor, username, key, minutes, actions);
        }
    }

    /**
     * 执行一次批次并返回已校验的结构化结果；截断交由调用方决定如何拆分。
     */
    public MinutesBatchExecutionResult generateSingleBatch(List<MinutesOutlineInput> batch, OutlineSourceSnapshot source, PromptVersion prompt,
                                                           String meetingId, OutlineView outline, long actor, String username, String key, MinutesTaskConfig config) throws Exception {
        long callId = prompts.startCall(prompt, meetingId, outline.getTranscriptVersion(), actor, key, outline.getVersion());
        long start = System.nanoTime();
        try {
            var sourceTool = new MinutesSourceTools(batch, source.getSourceSegments(), config.getMaxSourceSegments(), config.getMaxSourceChars(),
                    () -> meetings.requireOutlineEditor(meetingId, actor, false));
            meetings.requireOutlineEditor(meetingId, actor, false);
            var callbacks = org.springframework.ai.support.ToolCallbacks.from(sourceTool);
            String instructions = prompt.getContent();
            String input = json.writeValueAsString(batch);
            prompts.recordInput(callId, instructions, input);
            List<ExtractionToolTrace> traces = new ArrayList<>();
            String output = executor.execute(instructions, input, callbacks, config.getMaxToolCalls(), (call, returned) -> {
                var trace = new ExtractionToolTrace();
                trace.setCallId(call.id());
                trace.setName(call.name());
                trace.setArguments(call.arguments());
                trace.setResult(returned);
                traces.add(trace);
                prompts.recordToolTrace(callId, json.writeValueAsString(traces));
            });
            prompts.recordOutput(callId, output);
            var result = json.readValue(output, MinutesBatchResponse.class);
            validate(batch, result);
            prompts.finishCall(callId, BusinessStatus.SUCCEEDED.code(), (System.nanoTime() - start) / 1_000_000, null);
            var execution = new MinutesBatchExecutionResult();
            execution.setResult(result);
            execution.setAiCallId(callId);
            return execution;
        } catch (ModelOutputTruncatedException ex) {
            prompts.finishCall(callId, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000, "OUTPUT_TRUNCATED");
            throw ex;
        } catch (Exception ex) {
            prompts.finishCall(callId, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000, "GENERATION_FAILED");
            throw ex;
        }
    }

    private void validate(List<MinutesOutlineInput> batch, MinutesBatchResponse result) {
        if (result == null || result.getMinutes() == null || result.getActions() == null)
            throw new IllegalArgumentException("纪要结果格式无效");
        Map<Integer, Set<Integer>> allowed = new HashMap<>();
        for (var item : batch) {
            Set<Integer> ids = new HashSet<>();
            for (var candidate : item.getCandidates()) ids.addAll(candidate.getEvidenceIndices());
            allowed.put(item.getOutlineId(), ids);
        }
        Set<Integer> covered = new HashSet<>();
        for (var item : result.getMinutes()) {
            if (item == null || !covered.add(item.getOutlineId()) || !validMinutesItem(allowed, item))
                throw new IllegalArgumentException("纪要条目或来源无效");
        }
        if (!covered.equals(allowed.keySet())) throw new IllegalArgumentException("纪要未完整覆盖本批大纲");
        for (var item : result.getActions())
            if (item == null || item.getContent() == null || item.getContent().isBlank() || item.getNeedsReview() == null || !validSources(allowed, item.getOutlineId(), item.getSourceSegmentIds()))
                throw new IllegalArgumentException("行动项或来源无效");
    }

    private boolean validSources(Map<Integer, Set<Integer>> allowed, Integer outlineId, List<Integer> ids) {
        return allowed.containsKey(outlineId) && ids != null && !ids.isEmpty() && allowed.get(outlineId).containsAll(ids);
    }

    private boolean validMinutesItem(Map<Integer, Set<Integer>> allowed, MinutesBatchItem item) {
        if (item.getDiscussion() == null && item.getConclusions() == null)
            return item.getContent() != null && !item.getContent().isBlank() && item.getNeedsReview() != null
                    && validSources(allowed, item.getOutlineId(), item.getSourceSegmentIds());
        if (item.getDiscussion() == null || item.getConclusions() == null
                || item.getDiscussion().isEmpty() && item.getConclusions().isEmpty()) return false;
        return java.util.stream.Stream.concat(item.getDiscussion().stream(), item.getConclusions().stream())
                .allMatch(point -> point != null && point.getContent() != null && !point.getContent().isBlank()
                        && point.getNeedsReview() != null && validSources(allowed, item.getOutlineId(), point.getSourceSegmentIds()));
    }
}
