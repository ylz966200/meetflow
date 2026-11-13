package com.ylz.futuremade.identity.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 更新用户状态请求。 */
@Getter
@Setter
public class StatusRequest {
    /** 是否启用账号；不能为空。 */
    private Boolean enabled;

}
