package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 发送给大模型的会议问题与已授权参考纪要。
 */
@Getter
@Setter
public class KnowledgeQuestionInput {
    /**
     * 用户的问题。
     */
    private String question;
    /**
     * 最近的会话上下文，仅用于理解追问，不作为事实依据。
     */
    private List<KnowledgeConversationTurn> history;
    /**
     * 当前会议已确认纪要中的检索结果。
     */
    private List<KnowledgeSource> sources;
    /** 未召回纪要内容的会议名称，多会议问答时使用。 */
    private List<String> missingMeetings;
}
