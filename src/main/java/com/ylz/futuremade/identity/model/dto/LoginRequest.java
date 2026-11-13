package com.ylz.futuremade.identity.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 登录请求。 */
@Getter
@Setter
public class LoginRequest {
    /** 登录用户名。 */
    private String username;

    /** 登录密码，仅用于身份验证，不得记录日志。 */
    private String password;

}
