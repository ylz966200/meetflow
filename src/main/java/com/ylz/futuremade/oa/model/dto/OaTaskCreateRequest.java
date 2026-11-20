package com.ylz.futuremade.oa.model.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 从纪要确认创建本地待办的请求。 */
@Getter @Setter
public class OaTaskCreateRequest {
    /** 用户查看的纪要版本，用于并发校验。 */ private Integer minutesVersion;
    /** 所选行动项序号，从零开始。 */ private Integer actionIndex;
    /** 用户核对后的待办内容。 */ private String content;
    /** 系统用户登录名，必须是当前会议成员。 */ private String assigneeUsername;
    /** 用户明确确认的截止日期。 */ private LocalDate dueDate;
}
