package com.ylz.futuremade.common.enums;

/**
 * 项目持久化业务状态；数字一经使用不得调整，禁止通过 ordinal 持久化。
 */
public enum BusinessStatus {
    /**
     * 1：草稿，允许编辑。
     */
    DRAFT(1, "草稿"),
    /**
     * 2：已确认。
     */
    CONFIRMED(2, "已确认"),
    /**
     * 3：执行中。
     */
    RUNNING(3, "执行中"),
    /**
     * 4：执行成功。
     */
    SUCCEEDED(4, "成功"),
    /**
     * 5：执行失败。
     */
    FAILED(5, "失败"),
    /**
     * 6：等待服务端处理。
     */
    PROCESSING(6, "处理中"),
    /**
     * 7：提交结果不确定，需要查询确认。
     */
    UNKNOWN(7, "状态待确认"),
    /**
     * 8：正在向外部服务提交。
     */
    SUBMITTING(8, "提交中"),
    /**
     * 9：录音已上传。
     */
    UPLOADED(9, "已上传"),
    /**
     * 10：录音上传中。
     */
    UPLOADING(10, "上传中"),
    /**
     * 11：录音已提交转写。
     */
    SUBMITTED(11, "已提交"),
    /** 12：录音删除中，允许重试完成清理。 */
    DELETING(12, "删除中"),
    /** 13：录音对象已删除，保留关联元数据。 */
    DELETED(13, "已删除");
    /**
     * 稳定的数据库和接口状态码。
     */
    private final Integer code;
    /**
     * 状态的中文含义。
     */
    private final String description;

    BusinessStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer code() {
        return code;
    }

    public String description() {
        return description;
    }
}
