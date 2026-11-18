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
     * 同一会议最近几轮问答，不含当前问题。
     */
    private java.util.List<KnowledgeConversationTurn> history;
    /**
     * 历史对话使用的纪要版本；有历史时须与当前纪要版本一致。
     */
    private Integer minutesVersion;
}
