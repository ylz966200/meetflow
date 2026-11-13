-- 大纲每次修改保留独立版本；会议文字稿版本不被覆盖。
CREATE TABLE IF NOT EXISTS mf_outline_version (
    meeting_id CHAR(36) NOT NULL COMMENT '会议 ID',
    version INT NOT NULL COMMENT '大纲版本号',
    transcript_version INT NOT NULL COMMENT '关联的文字稿版本号',
    status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举',
    items_json TEXT NOT NULL COMMENT '按顺序保存大纲条目的 JSON',
    created_by BIGINT NOT NULL COMMENT '大纲版本创建人用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '大纲版本创建时间',
    PRIMARY KEY(meeting_id, version),
    CONSTRAINT fk_outline_meeting FOREIGN KEY (meeting_id) REFERENCES mf_meeting(id),
    CONSTRAINT fk_outline_author FOREIGN KEY (created_by) REFERENCES mf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议大纲版本表';
