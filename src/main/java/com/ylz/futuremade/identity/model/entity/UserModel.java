package com.ylz.futuremade.identity.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 系统用户表（mf_user）对应的数据模型。 */
@Getter
@Setter
public class UserModel {
    /** 用户主键 ID。 */
    private Long id;

    /** 登录用户名，区分大小写。 */
    private String username;

    /** BCrypt 密码哈希，禁止明文存储。 */
    private String passwordHash;

    /** 账号是否启用。 */
    private Boolean enabled;

    /** 账号创建时间。 */
    private LocalDateTime createdAt;

    /** 账号最后更新时间。 */
    private LocalDateTime updatedAt;

}
