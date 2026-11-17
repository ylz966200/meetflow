package com.ylz.futuremade.identity.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 登录成功后的令牌响应，禁止输出到日志。 */
@Getter
@Setter
public class LoginResponse {
    /** JWT 访问令牌，属于敏感信息。 */
    private String accessToken;
    /** 令牌类型。 */
    private String tokenType;
    /** 令牌有效期，单位秒。 */
    private int expiresIn;
    /** 当前登录用户名。 */
    private String username;
}
