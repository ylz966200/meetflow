package com.ylz.futuremade.identity.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 更新系统角色请求。 */
@Getter
@Setter
public class UpdateRoleRequest {
    /** 角色名称。 */
    private String name;

    /** 是否启用角色。 */
    private Boolean enabled;

}
