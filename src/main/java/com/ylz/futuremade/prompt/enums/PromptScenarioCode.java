package com.ylz.futuremade.prompt.enums;

/** 系统内置的提示词业务场景，编码与数据库和接口保持一致。 */
public enum PromptScenarioCode {
    /** 评审会议 AI 的输出结果。 */
    EVALUATION_JUDGE("EVALUATION_JUDGE"),
    /** 根据文字稿生成会议大纲。 */
    MEETING_OUTLINE("MEETING_OUTLINE"),
    /** 根据文字稿和大纲生成会议纪要。 */
    MEETING_MINUTES("MEETING_MINUTES"),
    /** 整理多个录音转写结果。 */
    TRANSCRIPT_ORGANIZE("TRANSCRIPT_ORGANIZE"),
    /** 按分段提取会议内容。 */
    TRANSCRIPT_EXTRACTION("TRANSCRIPT_EXTRACTION"),
    /** 汇总各分段提取的候选信息。 */
    TRANSCRIPT_SUMMARY("TRANSCRIPT_SUMMARY"),
    /** 将候选信息归并为议题。 */
    TRANSCRIPT_TOPIC_GROUPING("TRANSCRIPT_TOPIC_GROUPING");

    /** 已持久化的场景编码。 */
    private final String code;

    PromptScenarioCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
