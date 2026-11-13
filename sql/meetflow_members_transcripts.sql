-- 执行前确认目标数据库；不会修改已有会议数据。
CREATE TABLE IF NOT EXISTS mf_meeting_member (
    meeting_id CHAR(36) NOT NULL COMMENT '会议 ID',
    user_id BIGINT NOT NULL COMMENT '会议成员用户 ID',
    member_role VARCHAR(16) NOT NULL COMMENT '会议成员角色，编辑者或查看者',
    PRIMARY KEY (meeting_id, user_id),
    FOREIGN KEY (meeting_id) REFERENCES mf_meeting(id),
    FOREIGN KEY (user_id) REFERENCES mf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议成员表';

CREATE TABLE IF NOT EXISTS mf_transcript_version (
    meeting_id CHAR(36) NOT NULL COMMENT '会议 ID',
    version INT NOT NULL COMMENT '文字稿版本号',
    content MEDIUMTEXT NOT NULL COMMENT '会议文字稿正文，属于敏感数据',
    ai_draft_id CHAR(36) NULL COMMENT '来源 AI 整理草稿 ID，人工文字稿为空',
    created_by BIGINT NOT NULL COMMENT '文字稿版本创建人用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '文字稿版本创建时间',
    PRIMARY KEY (meeting_id, version),
    FOREIGN KEY (meeting_id) REFERENCES mf_meeting(id),
    FOREIGN KEY (created_by) REFERENCES mf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议文字稿版本表';
