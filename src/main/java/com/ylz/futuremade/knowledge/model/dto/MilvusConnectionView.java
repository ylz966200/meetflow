package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;

/**
 * 知识库连接检查结果，不包含地址、凭证或集合名称。
 */
@Getter
public class MilvusConnectionView {
    /**
     * 是否成功连接并完成只读请求。
     */
    private final boolean connected;
    /**
     * 面向管理员的连接检查说明。
     */
    private final String message;

    public MilvusConnectionView(boolean connected, String message) {
        this.connected = connected;
        this.message = message;
    }
}
