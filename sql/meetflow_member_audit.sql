-- 在目标数据库执行，记录会议成员权限变更，不记录文字稿内容。
CREATE TABLE IF NOT EXISTS mf_meeting_member_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '审计记录 ID',
    meeting_id CHAR(36) NOT NULL COMMENT '会议 ID',
    actor_id BIGINT NOT NULL COMMENT '操作人用户 ID',
    target_id BIGINT NOT NULL COMMENT '被操作的成员用户 ID',
    action VARCHAR(20) NOT NULL COMMENT '成员变更操作类型',
    old_role VARCHAR(16) NULL COMMENT '变更前成员角色，可为空',
    new_role VARCHAR(16) NULL COMMENT '变更后成员角色，可为空',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_member_audit_meeting_time (meeting_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议成员变更审计表';
