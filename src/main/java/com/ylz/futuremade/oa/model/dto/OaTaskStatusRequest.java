package com.ylz.futuremade.oa.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 待办状态变更请求。 */
@Getter @Setter
public class OaTaskStatusRequest {
    /** 页面所见原状态，用于并发校验。 */ private Integer expectedStatus;
    /** 目标状态编码。 */ private Integer status;
}
