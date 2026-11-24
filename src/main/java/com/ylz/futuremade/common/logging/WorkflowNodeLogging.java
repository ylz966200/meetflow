package com.ylz.futuremade.common.logging;

import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 记录 Graph 节点的执行位置和耗时，状态内容及执行令牌不进入日志。 */
public final class WorkflowNodeLogging {
    private static final Logger log = LoggerFactory.getLogger(WorkflowNodeLogging.class);

    private WorkflowNodeLogging() {}

    /** 包装业务节点；异常原样交给工作流处理并更新任务状态。 */
    public static AsyncNodeActionWithConfig loggedNode(String node, NodeActionWithConfig action) {
        return AsyncNodeActionWithConfig.node_async((state, config) -> {
            String taskId = config.threadId().orElseThrow();
            long started = System.nanoTime();
            log.info("工作流节点开始，taskId={}，node={}，round={}", taskId, node, state.value("mergeRound").orElse(null));
            try {
                Map<String, Object> updates = action.apply(state, config);
                log.info("工作流节点完成，taskId={}，node={}，durationMs={}", taskId, node,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                return updates;
            } catch (Exception exception) {
                log.warn("工作流节点失败，taskId={}，node={}，durationMs={}，exceptionType={}",
                        taskId, node, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        exception.getClass().getName());
                throw exception;
            }
        });
    }
}
