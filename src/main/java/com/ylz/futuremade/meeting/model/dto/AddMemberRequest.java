package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 添加会议成员请求。 */
@Getter
@Setter
public class AddMemberRequest {
    /** 待添加成员的登录名。 */
    private String username;

    /** 会议角色：EDITOR 或 VIEWER。 */
    private String role;

}
