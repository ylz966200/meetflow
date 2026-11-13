package com.ylz.futuremade.identity.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 用户系统角色分配请求。 */
@Getter
@Setter
public class RoleAssignmentRequest {
    /** 需要分配的系统角色 ID 列表。 */
    private List<Long> roleIds;

}
