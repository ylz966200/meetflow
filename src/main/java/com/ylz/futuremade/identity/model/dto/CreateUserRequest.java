package com.ylz.futuremade.identity.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 创建系统用户请求。 */
@Getter
@Setter
public class CreateUserRequest {
    /** 新用户的唯一登录名。 */
    private String username;

    /** 新用户初始密码，不得记录日志。 */
    private String password;

}
