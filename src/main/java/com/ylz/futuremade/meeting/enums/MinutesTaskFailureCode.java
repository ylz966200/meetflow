package com.ylz.futuremade.meeting.enums;

/** 纪要后台任务对外返回的稳定错误编码。 */
public enum MinutesTaskFailureCode {
    /** Graph 调度、执行或汇总失败，可保留已有批次结果后重试。 */
    GRAPH_FAILED;

    public String code() {
        return name();
    }
}
