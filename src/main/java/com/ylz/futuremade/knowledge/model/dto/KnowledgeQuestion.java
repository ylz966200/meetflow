package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 单场会议知识问答请求。
 */
@Getter
@Setter
public class KnowledgeQuestion {
    /**
     * 用户针对会议提出的问题。
     */
    private String question;
    /**
     * 当前页面会话的随机标识，后端结合用户、会议和版本隔离记忆。
     */
    private String conversationId;
    /**
     * 历史对话使用的纪要版本；有历史时须与当前纪要版本一致。
     */
    private Integer minutesVersion;
    /** 是否跳过已有缓存重新生成。 */
    private Boolean bypassCache;
}
