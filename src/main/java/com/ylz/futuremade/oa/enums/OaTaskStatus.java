package com.ylz.futuremade.oa.enums;

/** 本地 OA 待办状态，编码固定。 */
public enum OaTaskStatus {
    /** 1：等待处理。 */ PENDING(1),
    /** 2：负责人处理中。 */ IN_PROGRESS(2),
    /** 3：已完成。 */ COMPLETED(3),
    /** 4：已取消。 */ CANCELLED(4);
    private final int code;
    OaTaskStatus(int code) { this.code = code; }
    public int code() { return code; }
}
