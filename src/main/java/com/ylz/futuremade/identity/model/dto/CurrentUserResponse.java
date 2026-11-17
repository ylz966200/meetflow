package com.ylz.futuremade.identity.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 当前登录用户及其系统角色。 */
@Getter
@Setter
public class CurrentUserResponse {
    /** 当前登录用户名。 */
    private String username;
    /** 当前用户拥有的角色编码。 */
    private List<String> roles;
}
