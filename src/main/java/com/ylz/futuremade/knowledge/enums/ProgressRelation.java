package com.ylz.futuremade.knowledge.enums;
/** 进展关联判断，接口使用固定编码。 */
public enum ProgressRelation {
    /** 明确相关。 */ RELATED(1), /** 可能相关。 */ POSSIBLE(2);
    private final int code;
    ProgressRelation(int code) { this.code = code; }
    public int code() { return code; }
}
