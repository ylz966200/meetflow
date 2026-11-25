package com.ylz.futuremade.oa.enums;
/** 单会议追踪执行状态，使用固定编码。 */
public enum TrackingJobStatus {
    /** 待执行。 */ PENDING(1), /** 执行中。 */ RUNNING(2), /** 成功。 */ SUCCEEDED(3), /** 失败。 */ FAILED(4);
    private final int code;
    TrackingJobStatus(int code) { this.code = code; }
    public int code() { return code; }
}
