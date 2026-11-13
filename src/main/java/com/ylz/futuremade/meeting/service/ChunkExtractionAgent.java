package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.transcript.ChunkExtractionContext;
import com.ylz.futuremade.meeting.transcript.MeetingContextProperties;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单个分段的提取 Agent；服务端显式控制工具循环和可引用的来源。
 */
@Component
public class ChunkExtractionAgent {
    private static final Set<String> ALLOWED_TYPES = Set.of("FACT", "PROPOSAL", "OBJECTION", "DECISION", "ACTION", "QUESTION", "UNCERTAIN");
    private final DeepSeekChatModel model;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final MeetingContextProperties properties;

    public ChunkExtractionAgent(DeepSeekChatModel model, PromptService prompts, ObjectMapper json, MeetingContextProperties properties) {
        this.model = model;
        this.prompts = prompts;
        this.json = json;
        this.properties = properties;
    }

    /**
     * 输入、输出及工具轨迹属于敏感会议内容，仅写入受控调用记录。
     */
    public ChunkExtractionView extract(ChunkExtractionContext context, String systemPrompt, long callId) throws Exception {
        var chunk = context.getChunk();
        String userContent = json.writeValueAsString(chunk);
        prompts.recordInput(callId, systemPrompt, userContent);
        var callbacks = ToolCallbacks.from(context.getTools());
        var options = DeepSeekChatOptions.builder().toolCallbacks(callbacks).internalToolExecutionEnabled(false).build();
        var byName = Arrays.stream(callbacks).collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), callback -> callback));
        Set<Integer> current = chunk.getUtterances().stream().map(ChunkUtterance::getSegmentIndex).collect(Collectors.toSet());
        Set<Integer> visible = new HashSet<>(current);
        chunk.getContextBefore().forEach(item -> visible.add(item.getSegmentIndex()));
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userContent));
        List<ExtractionToolTrace> traces = new ArrayList<>();
        int toolCalls = 0;
        while (true) {
            var response = model.call(new Prompt(messages, options));
            if (response.hasFinishReasons(Set.of("length", "LENGTH")))
                throw new IllegalStateException("模型输出因长度限制被截断");
            if (!response.hasToolCalls()) {
                String output = response.getResult().getOutput().getText();
                // 多轮工具调用可能包含多次模型请求；暂不将最后一轮用量误记为总用量。
                prompts.recordOutput(callId, output);
                var result = json.readValue(output, ChunkExtractionView.class);
                validate(result, current, visible);
                result.setTaskId(chunk.getTaskId());
                result.setChunkIndex(chunk.getChunkIndex());
                result.setSpeakerReviewVersion(chunk.getSpeakerReviewVersion());
                return result;
            }
            var assistant = response.getResult().getOutput();
            messages.add(assistant);
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (var call : assistant.getToolCalls()) {
                if (++toolCalls > properties.getMaxCalls()) throw new IllegalStateException("上下文工具调用超过限制");
                var callback = byName.get(call.name());
                if (callback == null) throw new IllegalStateException("模型请求了未授权的工具");
                String returned = callback.call(call.arguments());
                var trace = new ExtractionToolTrace();
                trace.setCallId(call.id());
                trace.setName(call.name());
                trace.setArguments(call.arguments());
                trace.setResult(returned);
                traces.add(trace);
                prompts.recordToolTrace(callId, json.writeValueAsString(traces));
                var toolResult = json.readValue(returned, MeetingContextResult.class);
                toolResult.getUtterances().forEach(item -> visible.add(item.getSegmentIndex()));
                responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), returned));
            }
            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }
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
