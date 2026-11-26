package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.transcript.ChunkExtractionContext;
import com.ylz.futuremade.meeting.transcript.MeetingContextProperties;
import com.ylz.futuremade.assistant.service.ReactAgentExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.ai.tool.ToolCallback;
import java.util.concurrent.atomic.AtomicInteger;
import com.ylz.futuremade.assistant.tool.AuthorizedToolRegistry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 单个分段的提取 Agent；ReactAgent 编排工具循环，业务层校验可引用的来源。
 */
@Component
public class ChunkExtractionAgent {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChunkExtractionAgent.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("FACT", "PROPOSAL", "OBJECTION", "DECISION", "ACTION", "QUESTION", "UNCERTAIN");
    private final ReactAgentExecutor executor;
    private final AuthorizedToolRegistry tools;
    private final ObjectMapper json;
    private final MeetingContextProperties properties;

    public ChunkExtractionAgent(ReactAgentExecutor executor, AuthorizedToolRegistry tools, ObjectMapper json, MeetingContextProperties properties) {
        this.executor = executor;
        this.tools = tools;
        this.json = json;
        this.properties = properties;
    }

    /**
     * 输入、输出及工具轨迹属于敏感会议内容，仅写入受控调用记录。
     */
    public ChunkExtractionView extract(ChunkExtractionContext context, AiCallContext audit, String username) throws Exception {
        TranscriptChunkView chunk = context.getChunk();
        String userContent = json.writeValueAsString(chunk);
        ToolCallback[] callbacks = tools.forUserScoped(username, context.getTools());
        Set<Integer> current = chunk.getUtterances().stream().map(ChunkUtterance::getSegmentIndex).collect(Collectors.toSet());
        Set<Integer> visible = new HashSet<>(current);
        chunk.getContextBefore().forEach(item -> visible.add(item.getSegmentIndex()));
        AtomicInteger toolCalls = new AtomicInteger();
        String output = executor.execute("outline_chunk_extraction", audit, userContent, callbacks, null, response -> {
            int count = toolCalls.incrementAndGet();
            if (count == properties.getMaxCalls() + 1)
                log.warn("大纲分段提取工具调用超过提醒阈值，requestKey={}，threshold={}", audit.getRequestKey(), properties.getMaxCalls());
            MeetingContextResult toolResult;
            try {
                toolResult = json.readValue(response.getResult(), MeetingContextResult.class);
            } catch (JsonProcessingException ex) {
                // 工具结果格式错误属于业务执行失败，保留原因，不作为模型参数错误反馈。
                throw new IllegalStateException("无法解析文字稿上下文工具结果", ex);
            }
            toolResult.getUtterances().forEach(item -> visible.add(item.getSegmentIndex()));
        });
        ChunkExtractionView result = json.readValue(output, ChunkExtractionView.class);
        validate(result, current, visible);
        result.setTaskId(chunk.getTaskId());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setSpeakerReviewVersion(chunk.getSpeakerReviewVersion());
        return result;
    }

    private void validate(ChunkExtractionView result, Set<Integer> current, Set<Integer> visible) {
        if (result.getItems() == null || result.getItems().size() > 100)
            throw new IllegalArgumentException("分段提取结果格式无效");
        for (ChunkExtractionItem item : result.getItems()) {
            if (item == null || item.getTopic() == null || item.getTopic().isBlank() || item.getTopic().length() > 200
                    || !ALLOWED_TYPES.contains(item.getType()) || item.getContent() == null || item.getContent().isBlank()
                    || item.getContent().length() > 2000 || item.getNeedsReview() == null || item.getEvidenceIndices() == null
                    || item.getEvidenceIndices().isEmpty() || !visible.containsAll(item.getEvidenceIndices())
                    || item.getEvidenceIndices().stream().noneMatch(current::contains))
                throw new IllegalArgumentException("分段提取结果或来源无效");
        }
    }
}
