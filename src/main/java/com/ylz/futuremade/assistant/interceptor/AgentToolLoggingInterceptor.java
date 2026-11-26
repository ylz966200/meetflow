package com.ylz.futuremade.assistant.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 记录单次 Agent 执行中的工具调用，不记录模型生成的参数及工具结果。 */
public class AgentToolLoggingInterceptor extends ToolInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AgentToolLoggingInterceptor.class);
    private final String agentName;
    private final AiCallContext audit;
    private final AtomicInteger calls;
    private final Set<String> allowedTools;

    public AgentToolLoggingInterceptor(String agentName, AiCallContext audit, AtomicInteger calls, Set<String> allowedTools) {
        this.agentName = agentName;
        this.audit = audit;
        this.calls = calls;
        this.allowedTools = Set.copyOf(allowedTools);
    }

    @Override
    public String getName() {
        return "agent_tool_logging";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        int count = calls.incrementAndGet();
        long started = System.nanoTime();
        String toolName = allowedTools.contains(request.getToolName()) ? request.getToolName() : "未授权工具";
        log.info("Agent工具调用开始，requestKey={}，agent={}，aiCallId={}，tool={}，toolCallCount={}",
                audit.getRequestKey(), agentName, audit.getLastCallId(), toolName, count);
        try {
            ToolCallResponse response = handler.call(request);
            log.info("Agent工具调用完成，requestKey={}，agent={}，aiCallId={}，tool={}，toolCallCount={}，durationMs={}",
                    audit.getRequestKey(), agentName, audit.getLastCallId(), toolName, count, elapsed(started));
            return response;
        } catch (RuntimeException ex) {
            // 保留原异常供业务层处理，避免将工具参数或异常正文写入日志。
            log.warn("Agent工具调用失败，requestKey={}，agent={}，aiCallId={}，tool={}，toolCallCount={}，durationMs={}，exceptionType={}",
                    audit.getRequestKey(), agentName, audit.getLastCallId(), toolName, count, elapsed(started), ex.getClass().getName());
            throw ex;
        }
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
