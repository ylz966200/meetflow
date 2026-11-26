package com.ylz.futuremade.assistant.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记录单次 Agent 执行中的模型调用，不记录提示词及模型内容。
 */
public class AgentModelLoggingInterceptor extends ModelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AgentModelLoggingInterceptor.class);
    private final String agentName;
    private final AiCallContext audit;
    private final AtomicInteger calls;
    private final AtomicReference<RuntimeException> modelFailure;

    public AgentModelLoggingInterceptor(String agentName, AiCallContext audit, AtomicInteger calls,
                                        AtomicReference<RuntimeException> modelFailure) {
        this.agentName = agentName;
        this.audit = audit;
        this.calls = calls;
        this.modelFailure = modelFailure;
    }

    @Override
    public String getName() {
        return "agent_model_logging";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        int round = calls.incrementAndGet();
        long started = System.nanoTime();
        // 每轮由 Advisor 创建新的调用记录，避免沿用上一轮的 ID。
        audit.setLastCallId(null);
        log.info("Agent模型调用开始，requestKey={}，agent={}，round={}", audit.getRequestKey(), agentName, round);
        try {
            ModelResponse response = handler.call(request);
            if (modelFailure.get() != null) throw modelFailure.get();
            log.info("Agent模型调用完成，requestKey={}，agent={}，round={}，aiCallId={}，durationMs={}",
                    audit.getRequestKey(), agentName, round, audit.getLastCallId(), elapsed(started));
            return response;
        } catch (RuntimeException ex) {
            // 记录安全的诊断信息后继续抛出原异常，异常详情由调用方安全处理。
            log.warn("Agent模型调用失败，requestKey={}，agent={}，round={}，aiCallId={}，durationMs={}，exceptionType={}",
                    audit.getRequestKey(), agentName, round, audit.getLastCallId(), elapsed(started), ex.getClass().getName());
            throw ex;
        }
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
