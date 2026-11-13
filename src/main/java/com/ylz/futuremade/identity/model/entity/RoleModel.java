package com.ylz.futuremade.identity.model.entity;

import lombok.Getter;
import lombok.Setter;

/** 系统角色表（mf_role）对应的数据模型。 */
@Getter
@Setter
public class RoleModel {
    /** 角色主键 ID。 */
    private Long id;

    /** 系统角色编码，区分大小写。 */
    private String code;

    /** 角色显示名称。 */
    private String name;

    /** 角色是否启用。 */
    private Boolean enabled;

}
