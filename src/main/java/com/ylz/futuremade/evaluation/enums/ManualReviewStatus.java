package com.ylz.futuremade.evaluation.enums;
/** 知识问答评测的人工复核结果。 */
public enum ManualReviewStatus {
    /** 未复核。 */ PENDING(0),
    /** 人工确认通过。 */ PASSED(1),
    /** 人工确认不通过。 */ FAILED(2);
    /** 数据库存储编码。 */ private final int code;
    ManualReviewStatus(int code) { this.code = code; }
    public int code() { return code; }
}
