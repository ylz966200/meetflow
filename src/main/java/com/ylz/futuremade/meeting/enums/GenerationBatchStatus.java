package com.ylz.futuremade.meeting.enums;

/** 后台生成批次的稳定状态码。 */
public enum GenerationBatchStatus {
    /** 1：待执行。 */
    PENDING(1, "待执行"),
    /** 2：执行中。 */
    RUNNING(2, "执行中"),
    /** 3：已成功。 */
    SUCCEEDED(3, "已成功"),
    /** 4：已失败。 */
    FAILED(4, "已失败"),
    /** 5：已拆分。 */
    SPLIT(5, "已拆分"),
    /** 6：已取消。 */
    CANCELLED(6, "已取消");
    /** 数据库和接口使用的稳定编码。 */
    private final int code;
    /** 状态中文说明。 */
    private final String description;
    GenerationBatchStatus(int code, String description) { this.code = code; this.description = description; }
    public int code() { return code; }
    public String description() { return description; }
}
