package com.ylz.futuremade.prompt.enums;

/**
 * 系统内置的提示词业务场景，编码与数据库和接口保持一致。
 */
public enum PromptScenarioCode {
    /** 基于授权会议的资料开展多步研究。 */
    MEETING_RESEARCH("MEETING_RESEARCH"),
    /**
     * 基于预期事实与检索来源评审会议知识问答。
     */
    KNOWLEDGE_EVALUATION_JUDGE("KNOWLEDGE_EVALUATION_JUDGE"),
    /**
     * 基于两场会议纪要回答并标明来源。
     */
    MEETING_KNOWLEDGE_CROSS_QA("MEETING_KNOWLEDGE_CROSS_QA"),
    /**
     * 结合会话历史改写知识检索问题。
     */
    MEETING_KNOWLEDGE_QUERY_REWRITE("MEETING_KNOWLEDGE_QUERY_REWRITE"),
    /**
     * 基于会议纪要进行知识问答。
     */
    MEETING_KNOWLEDGE_QA("MEETING_KNOWLEDGE_QA"),
    /**
     * 评审会议 AI 的输出结果。
     */
    EVALUATION_JUDGE("EVALUATION_JUDGE"),
    /**
     * 根据文字稿生成会议大纲。
     */
    MEETING_OUTLINE("MEETING_OUTLINE"),
    /**
     * 根据文字稿和大纲生成会议纪要。
     */
    MEETING_MINUTES("MEETING_MINUTES"),
    /**
     * 整理多个录音转写结果。
     */
    TRANSCRIPT_ORGANIZE("TRANSCRIPT_ORGANIZE"),
    /**
     * 按分段提取会议内容。
     */
    TRANSCRIPT_EXTRACTION("TRANSCRIPT_EXTRACTION"),
    /**
     * 汇总各分段提取的候选信息。
     */
    TRANSCRIPT_SUMMARY("TRANSCRIPT_SUMMARY"),
    /**
     * 将候选信息归并为议题。
     */
    TRANSCRIPT_TOPIC_GROUPING("TRANSCRIPT_TOPIC_GROUPING"),
    /**
     * 审核候选信息与大纲主题的关联，识别需人工核对的问题。
     */
    TRANSCRIPT_OUTLINE_REVIEW("TRANSCRIPT_OUTLINE_REVIEW");

    /**
     * 已持久化的场景编码。
     */
    private final String code;

    PromptScenarioCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
