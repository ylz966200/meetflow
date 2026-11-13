package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;

/** 会议成员表（mf_meeting_member）对应的数据模型。 */
@Getter
@Setter
public class MeetingMemberModel {
    /** 会议 ID。 */
    private String meetingId;

    /** 会议成员用户 ID。 */
    private Long userId;

    /** 会议成员角色，编辑者或查看者。 */
    private String memberRole;

}
