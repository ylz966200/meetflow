package com.ylz.futuremade.common.logging;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 监听 Graph 节点执行位置，不记录状态内容或执行令牌。
 */
public final class WorkflowNodeLogging implements GraphLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(WorkflowNodeLogging.class);

    @Override
    public void before(String node, Map<String, Object> state, RunnableConfig config, Long timestamp) {
        log.info("工作流节点开始，taskId={}，node={}，round={}",
                config.threadId().orElse(null), node, state.get("mergeRound"));
    }

    @Override
    public void after(String node, Map<String, Object> state, RunnableConfig config, Long timestamp) {
        log.info("工作流节点完成，taskId={}，node={}", config.threadId().orElse(null), node);
    }

    @Override
    public void onError(String node, Map<String, Object> state, Throwable error, RunnableConfig config) {
        log.warn("工作流节点失败，taskId={}，node={}，exceptionType={}",
                config.threadId().orElse(null), node, error.getClass().getName());
    }
}
