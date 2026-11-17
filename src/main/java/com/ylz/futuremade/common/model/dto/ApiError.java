package com.ylz.futuremade.common.model.dto;
import lombok.Getter;
/** 对外业务错误信息，不包含内部异常。 */
@Getter
public class ApiError {
    /** 可展示给调用方的错误说明。 */
    private final String message;
    public ApiError(String message) { this.message = message; }
}
