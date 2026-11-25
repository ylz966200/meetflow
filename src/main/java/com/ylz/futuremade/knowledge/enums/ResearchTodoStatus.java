package com.ylz.futuremade.knowledge.enums;

/** 研究助手报告的步骤状态，编码用于接口传输。 */
public enum ResearchTodoStatus {
    PENDING(1, "待处理"), RUNNING(2, "进行中"), COMPLETED(3, "已完成");

    private final int code;
    private final String description;

    ResearchTodoStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() { return code; }
    public String description() { return description; }

    public static boolean supports(int code) {
        for (ResearchTodoStatus status : values()) {
            if (status.code == code) return true;
        }
        return false;
    }
}
