-- 确认目标数据库及备份后执行；依赖 meetflow_auth.sql 中的 mf_user。
CREATE TABLE IF NOT EXISTS mf_meeting (
    id CHAR(36) NOT NULL PRIMARY KEY COMMENT '会议 ID，UUID 字符串',
    owner_id BIGINT NOT NULL COMMENT '会议组织者用户 ID',
    title VARCHAR(200) NOT NULL COMMENT '会议主题',
    meeting_time DATETIME NULL COMMENT '会议时间，可为空',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会议创建时间',
    INDEX idx_meeting_owner_created (owner_id, created_at, id),
    CONSTRAINT fk_meeting_owner FOREIGN KEY (owner_id) REFERENCES mf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议基本信息表';
