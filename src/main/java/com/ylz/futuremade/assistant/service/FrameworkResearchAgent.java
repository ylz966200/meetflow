package com.ylz.futuremade.assistant.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import com.ylz.futuremade.meeting.model.dto.ExtractionToolTrace;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 让 Alibaba Agent Framework 编排研究会话，通过扩展点保留调用与工具审计。
 */
@Component
public class FrameworkResearchAgent {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FrameworkResearchAgent.class);
    private final AiModelGateway models;
    private final PromptService prompts;
    private final ObjectMapper json;

    public FrameworkResearchAgent(AiModelGateway models, PromptService prompts, ObjectMapper json) {
        this.models = models;
        this.prompts = prompts;
        this.json = json;
    }

    /**
     * 每次调用创建独立 Agent。工具由调用方按用户授权筛选。
     */
    public String execute(String name, AiCallContext audit, String content, ToolCallback[] callbacks) throws Exception {
        return execute(name, audit, content, callbacks, null);
    }

    /** 使用框架输出类型生成结构化响应约束。 */
    public String execute(String name, AiCallContext audit, String content, ToolCallback[] callbacks, Class<?> outputType) throws Exception {
        AtomicReference<RuntimeException> toolFailure = new AtomicReference<>();
        Set<String> allowed = new HashSet<>();
        List<ToolCallback> guarded = Arrays.stream(callbacks)
                .map(callback -> {
                    allowed.add(callback.getToolDefinition().name());
                    return protect(callback, toolFailure);
                }).toList();
        List<ExtractionToolTrace> traces = new ArrayList<>();
        com.alibaba.cloud.ai.graph.agent.Builder builder = ReactAgent.builder()
                .name(name)
                .systemPrompt(audit.getPrompt().getContent())
                .chatOptions(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                .model(prompt -> {
                    traces.clear();
                    ChatResponse response = models.call(audit, prompt);
                    if (response.hasFinishReasons(Set.of("length", "LENGTH")))
                        throw new ModelOutputTruncatedException();
                    return response;
                })
                .tools(guarded)
                .parallelToolExecution(false)
                .wrapSyncToolsAsAsync(false)
                .interceptors(new ToolInterceptor() {
                    @Override
                    public String getName() {
                        return "research_tool_audit";
                    }

                    @Override
                    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
                        if (!allowed.contains(request.getToolName()))
                            throw new IllegalStateException("模型请求了未授权的工具");
                        ToolCallResponse response = handler.call(request);
                        RuntimeException failure = toolFailure.getAndSet(null);
                        if (failure != null) {
                            String feedback = ToolCallingExecutor.argumentErrorFeedback(failure);
                            if (feedback == null) {
                                log.warn("研究工具执行失败，agent={}，tool={}，调用记录 ID={}",
                                        name, request.getToolName(), audit.getLastCallId());
                                throw failure;
                            }
                            response = ToolCallResponse.of(request.getToolCallId(), request.getToolName(), feedback);
                        }
                        record(audit, traces, request, response);
                        return response;
                    }
                });
        if (outputType != null) builder.outputType(outputType);
        ReactAgent agent = builder.build();
        return agent.call(new UserMessage(content)).getText();
    }

    private void record(AiCallContext audit, List<ExtractionToolTrace> traces,
                        ToolCallRequest request, ToolCallResponse response) {
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

    private ToolCallback protect(ToolCallback delegate, AtomicReference<RuntimeException> failure) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String arguments) {
                return call(arguments, null);
            }

            @Override
            public String call(String arguments, ToolContext context) {
                try {
                    return delegate.call(arguments, context);
                } catch (RuntimeException ex) {
                    // 1.1.2.0 工具节点会直接打印异常详情。由外层拦截器处理原异常，避免泄露会议内容。
                    failure.set(ex);
                    return "";
                }
            }
        };
    }
}
