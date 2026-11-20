package com.ylz.futuremade.oa.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 会议行动项创建的本地 OA 待办。 */
@Getter @Setter
public class OaTask {
    /** 待办编号。 */ private String id;
    /** 来源会议编号。 */ private String meetingId;
    /** 来源纪要版本。 */ private Integer minutesVersion;
    /** 来源行动项序号，从零开始。 */ private Integer actionIndex;
    /** 经确认的待办内容。 */ private String content;
    /** 负责人用户编号。 */ private Long assigneeId;
    /** 确认的截止日期。 */ private LocalDate dueDate;
    /** 状态编码，见 OaTaskStatus。 */ private Integer status;
    /** 创建人用户编号。 */ private Long createdBy;
    /** 创建时间。 */ private LocalDateTime createdAt;
    /** 更新时间。 */ private LocalDateTime updatedAt;
}
