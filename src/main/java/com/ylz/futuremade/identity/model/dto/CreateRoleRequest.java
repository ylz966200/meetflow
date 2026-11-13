package com.ylz.futuremade.identity.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 创建系统角色请求。 */
@Getter
@Setter
public class CreateRoleRequest {
    /** 角色编码，须以大写字母开头。 */
    private String code;

    /** 角色名称。 */
    private String name;

}
