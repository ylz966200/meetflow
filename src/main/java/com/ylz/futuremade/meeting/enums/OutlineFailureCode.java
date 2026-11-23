package com.ylz.futuremade.meeting.enums;

/** 大纲生成任务对外暴露的安全失败类型。 */
public enum OutlineFailureCode {
    /** 分段正文较长，但多次提取结果均为空。 */
    SUSPICIOUS_EMPTY,
    /** Graph 归并或保存阶段执行失败。 */
    GRAPH_FAILED;

    /** 返回稳定的数据库和接口编码。 */
    public String code() { return name(); }
}
