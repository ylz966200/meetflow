package com.ylz.futuremade.assistant.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.ylz.futuremade.meeting.model.dto.ExtractionToolTrace;
import com.ylz.futuremade.prompt.service.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行模型工具调用循环，达到次数提醒阈值后记录日志并继续执行。
 */
@Component
public class ToolCallingExecutor {
    private static final Logger log = LoggerFactory.getLogger(ToolCallingExecutor.class);
    // 只允许本次业务提供的工具，禁用从全局解析器查找其他工具。
    private final ToolCallingManager toolManager = ToolCallingManager.builder()
            .toolCallbackResolver(name -> { throw new IllegalStateException("模型请求了未授权的工具"); })
            .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true))
            .build();
    private final AiModelGateway models;

    private final PromptService prompts;
    private final ObjectMapper json;

    public ToolCallingExecutor(AiModelGateway models, PromptService prompts, ObjectMapper json) {
        this.prompts = prompts;
        this.json = json;
        this.models = models;
    }

    @FunctionalInterface
    public interface ToolResultHandler {
        void accept(AssistantMessage.ToolCall call, String result) throws Exception;
    }

    public String execute(AiCallContext audit, String content, ToolCallback[] callbacks, int maxCalls, ToolResultHandler handler) throws Exception {
        ToolCallingChatOptions modelOptions = options(callbacks);
        List<Message> messages = List.of(new SystemMessage(audit.getPrompt().getContent()), new UserMessage(content));
        AtomicInteger count = new AtomicInteger();
        while (true) {
            ChatResponse response = models.call(audit, new Prompt(messages, modelOptions));
            if (response.hasFinishReasons(Set.of("length", "LENGTH")))
                throw new ModelOutputTruncatedException();
            if (!response.hasToolCalls()) return response.getResult().getOutput().getText();

            // 使用框架的同步执行顺序关联工具调用 ID，逐次保留项目的审计与来源校验。
            Iterator<AssistantMessage.ToolCall> calls = response.getResults().stream()
                    .map(generation -> generation.getOutput().getToolCalls())
                    .filter(toolCalls -> !toolCalls.isEmpty())
                    .findFirst().orElseThrow().iterator();
            List<ExtractionToolTrace> traces = new ArrayList<>();
            ToolCallback[] audited = Arrays.stream(callbacks)
                    .map(callback -> auditedCallback(callback, calls, audit, count, maxCalls, traces, handler))
                    .toArray(ToolCallback[]::new);
            try {
                messages = toolManager.executeToolCalls(new Prompt(messages, options(audited)), response)
                        .conversationHistory();
            } catch (ToolAuditException ex) {
                throw ex.getCause();
            }
        }
    }

    private ToolCallingChatOptions options(ToolCallback[] callbacks) {
        return ToolCallingChatOptions.builder().toolCallbacks(callbacks).internalToolExecutionEnabled(false).build();
    }

    private ToolCallback auditedCallback(ToolCallback delegate, Iterator<AssistantMessage.ToolCall> calls,
            AiCallContext audit, AtomicInteger count, int maxCalls, List<ExtractionToolTrace> traces,
            ToolResultHandler handler) {
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
                AssistantMessage.ToolCall call = calls.next();
                if (!call.name().equals(delegate.getToolDefinition().name()))
                    throw new IllegalStateException("框架工具执行顺序与审计记录不一致");
                int sequence = count.incrementAndGet();
                if (sequence == maxCalls + 1)
                    log.warn("本次 AI 任务的工具调用次数已超过提醒阈值（{} 次），继续执行", maxCalls);
                long started = System.nanoTime();
                log.info("模型工具调用开始，aiCallId={}，tool={}，toolCallCount={}", audit.getLastCallId(), call.name(), sequence);
                String result;
                boolean validArguments = true;
                try {
                    result = delegate.call(arguments, context);
                } catch (RuntimeException ex) {
                    result = argumentErrorFeedback(ex);
                    if (result == null) throw ex;
                    log.warn("AI 工具参数格式错误，工具={}，请模型修正后重试", call.name());
                    validArguments = false;
                }

                ExtractionToolTrace trace = new ExtractionToolTrace();
                trace.setCallId(call.id());
                trace.setName(call.name());
                trace.setArguments(call.arguments());
                trace.setResult(result);
                traces.add(trace);
                try {
                    prompts.recordToolTrace(audit.getLastCallId(), json.writeValueAsString(traces));
                    if (validArguments) handler.accept(call, result);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    // 框架回调不声明受检异常，由执行入口重新抛出原异常。
                    throw new ToolAuditException(ex);
                }
                log.info("模型工具调用完成，aiCallId={}，tool={}，validArguments={}，durationMs={}",
                        audit.getLastCallId(), call.name(), validArguments, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                return result;
            }
        };
    }

    private static final class ToolAuditException extends RuntimeException {
        private ToolAuditException(Exception cause) {
            super(cause);
        }

        @Override
        public synchronized Exception getCause() {
            return (Exception) super.getCause();
        }
    }

    /**
     * 仅将参数反序列化错误反馈给模型，业务异常仍按原路径处理。
     */
    static String argumentErrorFeedback(Throwable failure) {
        JsonProcessingException formatError = findJsonError(failure);
        if (formatError == null) return null;
        return "工具参数格式无效（字段：" + errorField(formatError)
                + "）。请按照工具参数定义传入正确的 JSON 类型；整数编号必须是单个数字，不要使用范围或说明文字。";
    }

    private static JsonProcessingException findJsonError(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonProcessingException jsonError) return jsonError;
            if (cause.getCause() == cause) break;
        }
        return null;
    }

    /**
     * 只输出字段路径，不输出模型参数值或会议正文。
     */
    private static String errorField(JsonProcessingException exception) {
        if (exception instanceof JsonMappingException mapping) {
            for (JsonMappingException.Reference reference : mapping.getPath()) {
                String name = reference.getFieldName();
                if (name != null && name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) return name;
            }
        }
        return "未知字段";
    }
}
