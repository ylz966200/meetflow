package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 会议成员变更审计表（mf_meeting_member_audit）对应的数据模型。 */
@Getter
@Setter
public class MeetingMemberAuditModel {
    /** 审计记录 ID。 */
    private Long id;

    /** 会议 ID。 */
    private String meetingId;

    /** 操作人用户 ID。 */
    private Long actorId;

    /** 被操作的成员用户 ID。 */
    private Long targetId;

    /** 成员变更操作类型。 */
    private String action;

    /** 变更前成员角色，可为空。 */
    private String oldRole;

    /** 变更后成员角色，可为空。 */
    private String newRole;

    /** 操作时间。 */
    private LocalDateTime createdAt;

}
