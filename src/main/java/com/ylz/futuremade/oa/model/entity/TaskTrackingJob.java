package com.ylz.futuremade.oa.model.entity;
import lombok.Getter;
import lombok.Setter;
/** 单会议研究持久任务，租约到期后可恢复执行。 */
@Getter @Setter
public class TaskTrackingJob {
    /** 子任务编号。 */ private String id;
    /** 一次追踪的批号。 */ private String runId;
    /** 本地待办编号。 */ private String taskId;
    /** 待办所属会议编号。 */ private String originMeetingId;
    /** 研究会议编号。 */ private String meetingId;
    /** 固定纪要版本。 */ private Integer minutesVersion;
    /** 发起用户编号。 */ private Long actorId;
    /** 发起用户名，用于原工具权限检查。 */ private String username;
    /** 状态编码：1待执行、2执行中、3成功、4失败。 */ private Integer status;
    /** 当前执行令牌，禁止向接口暴露。 */ private String token;
    /** 成功时为研究结果与来源快照，失败时为脱敏原因 JSON 字符串。 */ private String resultJson;
    /** 租约到期时间。 */ private java.time.LocalDateTime leaseUntil;
    /** 创建时间。 */ private java.time.LocalDateTime createdAt;
}
