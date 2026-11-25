package com.ylz.futuremade.oa.model.entity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
/** 用户确认的待办与会议纪要条目的关联。 */
@Getter @Setter
public class TaskTrackingLink {
    /** 关联编号。 */ private String id;
    /** 本地待办编号。 */ private String taskId;
    /** 来源会议编号。 */ private String sourceMeetingId;
    /** 来源纪要版本。 */ private Integer minutesVersion;
    /** 来源条目序号，从零开始。 */ private Integer sectionIndex;
    /** 确认关联的用户编号。 */ private Long createdBy;
    /** 确认时间。 */ private LocalDateTime createdAt;
}
