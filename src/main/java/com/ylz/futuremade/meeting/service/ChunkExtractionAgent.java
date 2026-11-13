package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.transcript.ChunkExtractionContext;
import com.ylz.futuremade.meeting.transcript.MeetingContextProperties;
import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.assistant.service.ToolCallingExecutor;
import com.ylz.futuremade.assistant.tool.AuthorizedToolRegistry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单个分段的提取 Agent；服务端显式控制工具循环和可引用的来源。
 */
@Component
public class ChunkExtractionAgent {
    private static final Set<String> ALLOWED_TYPES = Set.of("FACT", "PROPOSAL", "OBJECTION", "DECISION", "ACTION", "QUESTION", "UNCERTAIN");
    private final ToolCallingExecutor executor;
    private final AuthorizedToolRegistry tools;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final MeetingContextProperties properties;

    public ChunkExtractionAgent(ToolCallingExecutor executor, AuthorizedToolRegistry tools, PromptService prompts, ObjectMapper json, MeetingContextProperties properties) {
        this.executor = executor;
        this.tools = tools;
        this.prompts = prompts;
        this.json = json;
        this.properties = properties;
    }

    /**
     * 输入、输出及工具轨迹属于敏感会议内容，仅写入受控调用记录。
     */
    public ChunkExtractionView extract(ChunkExtractionContext context, String systemPrompt, long callId, String username) throws Exception {
        var chunk = context.getChunk();
        String userContent = json.writeValueAsString(chunk);
        prompts.recordInput(callId, systemPrompt, userContent);
        var callbacks = tools.forUserScoped(username, context.getTools());
        Set<Integer> current = chunk.getUtterances().stream().map(ChunkUtterance::getSegmentIndex).collect(Collectors.toSet());
        Set<Integer> visible = new HashSet<>(current);
        chunk.getContextBefore().forEach(item -> visible.add(item.getSegmentIndex()));
        List<ExtractionToolTrace> traces = new ArrayList<>();
        String output = executor.execute(systemPrompt, userContent, callbacks, properties.getMaxCalls(), (call, returned) -> {
            var trace = new ExtractionToolTrace();
            trace.setCallId(call.id());
            trace.setName(call.name());
            trace.setArguments(call.arguments());
            trace.setResult(returned);
            traces.add(trace);
            prompts.recordToolTrace(callId, json.writeValueAsString(traces));
            var toolResult = json.readValue(returned, MeetingContextResult.class);
            toolResult.getUtterances().forEach(item -> visible.add(item.getSegmentIndex()));
        });
        // 多次模型请求的 token 用量暂不合并，避免误记。
        prompts.recordOutput(callId, output);
        var result = json.readValue(output, ChunkExtractionView.class);
        validate(result, current, visible);
        result.setTaskId(chunk.getTaskId());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setSpeakerReviewVersion(chunk.getSpeakerReviewVersion());
        return result;
    }

    private void validate(ChunkExtractionView result, Set<Integer> current, Set<Integer> visible) {
        if (result.getItems() == null || result.getItems().size() > 100)
            throw new IllegalArgumentException("分段提取结果格式无效");
        for (var item : result.getItems()) {
            if (item == null || item.getTopic() == null || item.getTopic().isBlank() || item.getTopic().length() > 200
                    || !ALLOWED_TYPES.contains(item.getType()) || item.getContent() == null || item.getContent().isBlank()
                    || item.getContent().length() > 2000 || item.getNeedsReview() == null || item.getEvidenceIndices() == null
                    || item.getEvidenceIndices().isEmpty() || !visible.containsAll(item.getEvidenceIndices())
                    || item.getEvidenceIndices().stream().noneMatch(current::contains))
                throw new IllegalArgumentException("分段提取结果或来源无效");
        }
    }
}
