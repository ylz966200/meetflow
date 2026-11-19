package com.ylz.futuremade.assistant.model.dto;

import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * 一次同步业务或后台批次的调用上下文，按执行实例创建。
 */
@Getter
@RequiredArgsConstructor
public class AiCallContext {
    /**
     * 本次使用的提示词版本。
     */
    private final PromptVersion prompt;
    /**
     * 所属会议 ID。
     */
    private final String meetingId;
    /**
     * 关联文字稿版本，可为空。
     */
    private final Integer transcriptVersion;
    /**
     * 发起人用户 ID。
     */
    private final Long actorId;
    /**
     * 同步业务请求或后台批次标识，可为空。
     */
    private final String requestKey;
    /**
     * 关联大纲版本，可为空。
     */
    private final Integer outlineVersion;
    /**
     * 本次业务中最近一次实际模型请求的调用记录 ID。
     */
    @Setter
    private Long lastCallId;
}
