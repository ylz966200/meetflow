package com.ylz.futuremade.assistant.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import com.ylz.futuremade.assistant.service.ToolCallingExecutor;
import com.ylz.futuremade.meeting.model.dto.ExtractionToolTrace;
import com.ylz.futuremade.prompt.service.PromptService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 研究 Agent 的工具授权、参数错误反馈和调用审计；每次执行创建独立实例。
 */
public class ResearchToolAuditInterceptor extends ToolInterceptor {
    private final Set<String> allowedTools;
    private final AtomicReference<RuntimeException> toolFailure;
    private final AiCallContext audit;
    private final List<ExtractionToolTrace> traces;
    private final PromptService prompts;
    private final ObjectMapper json;

    public ResearchToolAuditInterceptor(Set<String> allowedTools, AtomicReference<RuntimeException> toolFailure,
                                        AiCallContext audit, List<ExtractionToolTrace> traces,
                                        PromptService prompts, ObjectMapper json) {
        this.allowedTools = Set.copyOf(allowedTools);
        this.toolFailure = toolFailure;
        this.audit = audit;
        this.traces = traces;
        this.prompts = prompts;
        this.json = json;
    }

    @Override
    public String getName() {
        return "research_tool_audit";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        if (!allowedTools.contains(request.getToolName()))
            throw new IllegalStateException("模型请求了未授权的工具");
        ToolCallResponse response = handler.call(request);
        RuntimeException failure = toolFailure.getAndSet(null);
        if (failure != null) {
            String feedback = ToolCallingExecutor.argumentErrorFeedback(failure);
            if (feedback == null) throw failure;
            response = ToolCallResponse.of(request.getToolCallId(), request.getToolName(), feedback);
        }
        record(request, response);
        return response;
    }

    private void record(ToolCallRequest request, ToolCallResponse response) {
        ExtractionToolTrace trace = new ExtractionToolTrace();
        trace.setCallId(request.getToolCallId());
        trace.setName(request.getToolName());
        trace.setArguments(request.getArguments());
        trace.setResult(response.getResult());
        traces.add(trace);
        try {
            prompts.recordToolTrace(audit.getLastCallId(), json.writeValueAsString(traces));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化工具调用审计", ex);
        }
    }
}
