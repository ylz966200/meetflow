package com.ylz.futuremade.knowledge.enums;

/** 共用后台任务表中的知识模块任务类型。 */
public enum KnowledgeTaskType {
    /** 已确认纪要向量索引。 */ MINUTES_INDEX("MINUTES_INDEX");
    private final String code;
    KnowledgeTaskType(String code) { this.code = code; }
    public String code() { return code; }
}
