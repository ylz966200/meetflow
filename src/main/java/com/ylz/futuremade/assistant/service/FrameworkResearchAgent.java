package com.ylz.futuremade.assistant.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.ylz.futuremade.assistant.hook.AgentLifecycleLoggingHook;
import com.ylz.futuremade.assistant.interceptor.AgentModelLoggingInterceptor;
import com.ylz.futuremade.assistant.interceptor.AgentToolLoggingInterceptor;
import com.ylz.futuremade.assistant.interceptor.ResearchToolAuditInterceptor;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 让 Alibaba Agent Framework 编排研究会话，通过扩展点保留调用与工具审计。
 */
@Component
public class FrameworkResearchAgent {
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

    /**
     * 使用框架输出类型生成结构化响应约束。
     */
    public String execute(String name, AiCallContext audit, String content, ToolCallback[] callbacks, Class<?> outputType) throws Exception {
        AtomicReference<RuntimeException> toolFailure = new AtomicReference<>();
        AtomicReference<RuntimeException> modelFailure = new AtomicReference<>();
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AgentLifecycleLoggingHook lifecycleLogging = new AgentLifecycleLoggingHook(name, audit, modelCalls, toolCalls, modelFailure);
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
                    ChatResponse response;
                    try {
                        response = models.call(audit, prompt);
                        if (response.hasFinishReasons(Set.of("length", "LENGTH")))
                            throw new ModelOutputTruncatedException();
                    } catch (RuntimeException ex) {
                        // 框架模型节点可能吞掉异常；保存原异常供调用结束后重新抛出。
                        modelFailure.set(ex);
                        throw ex;
                    }
                    return response;
                })
                .tools(guarded)
                .parallelToolExecution(false)
                .wrapSyncToolsAsAsync(false)
                .hooks(lifecycleLogging)
                .interceptors(new AgentModelLoggingInterceptor(name, audit, modelCalls, modelFailure))
                .interceptors(new AgentToolLoggingInterceptor(name, audit, toolCalls, allowed))
                .interceptors(new ResearchToolAuditInterceptor(allowed, toolFailure, audit, traces, prompts, json));
        if (outputType != null) builder.outputType(outputType);
        ReactAgent agent = builder.build();
        try {
            String answer = agent.call(new UserMessage(content)).getText();
            if (modelFailure.get() != null) throw modelFailure.get();
            return answer;
        } catch (Exception ex) {
            // afterAgent 不保证在异常时执行，保留失败日志并继续抛出原异常。
            lifecycleLogging.logFailure(ex);
            throw ex;
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
