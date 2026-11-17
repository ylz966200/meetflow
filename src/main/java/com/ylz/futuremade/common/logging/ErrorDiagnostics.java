package com.ylz.futuremade.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 输出异常类型和调用栈，避免外部服务异常消息泄露会议数据或凭证。 */
public final class ErrorDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(ErrorDiagnostics.class);

    private ErrorDiagnostics() {}

    /** 业务描述只使用固定文案，不拼接会议正文或请求参数。 */
    public static void error(String operation, Throwable exception) {
        log.error("{}：异常类型和调用栈如下（原始异常消息可能包含敏感数据，未写入控制台）", operation, safeTrace(exception));
    }

    private static Throwable safeTrace(Throwable exception) {
        var copy = new RuntimeException(exception.getClass().getName());
        copy.setStackTrace(exception.getStackTrace());
        if (exception.getCause() != null && exception.getCause() != exception)
            copy.initCause(safeTrace(exception.getCause()));
        return copy;
    }
}
