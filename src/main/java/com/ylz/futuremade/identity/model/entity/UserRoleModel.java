package com.ylz.futuremade.identity.model.entity;

import lombok.Getter;
import lombok.Setter;

/** 系统用户角色关联表（mf_user_role）对应的数据模型。 */
@Getter
@Setter
public class UserRoleModel {
    /** 用户 ID。 */
    private Long userId;

    /** 系统角色 ID。 */
    private Long roleId;

}
