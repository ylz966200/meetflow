package com.ylz.futuremade.oa.service;

import com.ylz.futuremade.assistant.service.ModelOutputTruncatedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/** 从框架包装后的异常中提取可保存、可展示的业务错误。 */
final class TrackingFailureDetails {
    static final String DEFAULT_MESSAGE = "研究执行异常，请按子任务编号查询服务日志";
    private static final Pattern LENGTH_ERROR = Pattern.compile(
            "(子研究目标|研究输入)长度为 [0-9]+ 字符，超过上限 [0-9]+ 字符");
    private static final List<String> SAFE_REASONS = List.of(
            "研究输入不能为空",
            "所选会议的最新纪要索引尚未完成", "提示词尚未发布，请联系管理员",
            "所选纪要版本已变化", "会议纪要版本已变化", "会议纪要版本已变化，请重新研究",
            "研究期间纪要已变化，请重新研究", "研究用户已失效",
            "缺少会议研究工具权限，请联系管理员授权",
            "模型请求了未授权的工具", "研究未取得可验证的会议来源");

    private TrackingFailureDetails() {}

    static String describe(Throwable failure) {
        String fallback = DEFAULT_MESSAGE;
        // 包装层的异常消息可能包含模型内容，仅允许固定业务文案及纯数字长度。
        for (Throwable cause : org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList(failure)) {
            if (cause instanceof ModelOutputTruncatedException) return "模型输出被截断，请缩小研究范围后重试";
            String reason = cause instanceof ResponseStatusException status ? status.getReason() : cause.getMessage();
            if (reason != null && (SAFE_REASONS.contains(reason) || LENGTH_ERROR.matcher(reason).matches())) return reason;
            if (cause instanceof ResponseStatusException status)
                fallback = "会议研究请求失败（HTTP " + status.getStatusCode().value() + "），请按子任务编号查询服务日志";
        }
        return fallback;
    }
}
