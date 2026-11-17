package com.ylz.futuremade.meeting.enums;

/** 后台生成任务的稳定状态码。 */
public enum GenerationTaskStatus {
    /** 1：待执行。 */
    PENDING(1, "待执行"),
    /** 2：执行中。 */
    RUNNING(2, "执行中"),
    /** 3：部分失败。 */
    PARTIAL_FAILED(3, "部分失败"),
    /** 4：已完成。 */
    SUCCEEDED(4, "已完成"),
    /** 5：来源已变化。 */
    SOURCE_CHANGED(5, "来源已变化"),
    /** 6：已取消。 */
    CANCELLED(6, "已取消");
    /** 数据库和接口使用的稳定编码。 */
    private final int code;
    /** 状态中文说明。 */
    private final String description;
    GenerationTaskStatus(int code, String description) { this.code = code; this.description = description; }
    public int code() { return code; }
    public String description() { return description; }
}
