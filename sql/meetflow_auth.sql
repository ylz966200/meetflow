-- 在确认目标数据库后手动执行；不创建默认密码或用户。
CREATE TABLE IF NOT EXISTS mf_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键 ID',
    username VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '登录用户名，区分大小写',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，禁止明文存储',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '账号是否启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '账号最后更新时间',
    UNIQUE KEY uk_mf_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';


CREATE TABLE IF NOT EXISTS mf_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '角色主键 ID',
    code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '系统角色编码，区分大小写',
    name VARCHAR(100) NOT NULL COMMENT '角色显示名称',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '角色是否启用',
    UNIQUE KEY uk_mf_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS mf_user_role (
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '系统角色 ID',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_mf_user_role_user FOREIGN KEY (user_id) REFERENCES mf_user(id),
    CONSTRAINT fk_mf_user_role_role FOREIGN KEY (role_id) REFERENCES mf_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户角色关联表';
