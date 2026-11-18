package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 单场会议的历史一轮问答，供当前请求参考。
 */
@Getter
@Setter
public class KnowledgeConversationTurn {
    /**
     * 用户之前提出的问题。
     */
    private String question;
    /**
     * 助手之前的回答，不作为会议事实来源。
     */
    private String answer;
}
