package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 变更会议成员角色请求。 */
@Getter
@Setter
public class ChangeMemberRequest {
    /** 客户端读取到的原会议角色，用于冲突检测。 */
    private String expectedRole;

    /** 新的会议角色：EDITOR 或 VIEWER。 */
    private String newRole;

}
