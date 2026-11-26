package com.ylz.futuremade.assistant.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记录一次 Agent 执行的生命周期；每次执行创建独立实例。
 */
public class AgentLifecycleLoggingHook extends AgentHook {
    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleLoggingHook.class);
    private final String agentName;
    private final AiCallContext audit;
    private final AtomicInteger modelCalls;
    private final AtomicInteger toolCalls;
    private final AtomicReference<RuntimeException> modelFailure;
    private long started = System.nanoTime();

    public AgentLifecycleLoggingHook(String agentName, AiCallContext audit,
                                     AtomicInteger modelCalls, AtomicInteger toolCalls,
                                     AtomicReference<RuntimeException> modelFailure) {
        this.agentName = agentName;
        this.audit = audit;
        this.modelCalls = modelCalls;
        this.toolCalls = toolCalls;
        this.modelFailure = modelFailure;
    }

    @Override
    public String getName() {
        return "agent_lifecycle_logging";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        started = System.nanoTime();
        log.info("Agent开始，requestKey={}，agent={}", audit.getRequestKey(), agentName);
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        // 框架可能将模型异常转换为结果，不能因此把这次执行记为成功。
        if (modelFailure.get() != null) return CompletableFuture.completedFuture(Map.of());
        log.info("Agent完成，requestKey={}，agent={}，durationMs={}，modelCalls={}，toolCalls={}",
                audit.getRequestKey(), agentName, elapsed(), modelCalls.get(), toolCalls.get());
        return CompletableFuture.completedFuture(Map.of());
    }

    /**
     * 异常可能绕过 afterAgent，由调用方记录失败后继续抛出原异常。
     */
    public void logFailure(Exception exception) {
        log.warn("Agent失败，requestKey={}，agent={}，durationMs={}，modelCalls={}，toolCalls={}，exceptionType={}",
                audit.getRequestKey(), agentName, elapsed(), modelCalls.get(), toolCalls.get(), exception.getClass().getName());
    }

    private long elapsed() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
