package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 会议基本信息表（mf_meeting）对应的数据模型。 */
@Getter
@Setter
public class MeetingModel {
    /** 会议 ID，UUID 字符串。 */
    private String id;

    /** 会议组织者用户 ID。 */
    private Long ownerId;

    /** 会议主题。 */
    private String title;

    /** 会议时间，可为空。 */
    private LocalDateTime meetingTime;

    /** 会议创建时间。 */
    private LocalDateTime createdAt;

}
