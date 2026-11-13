-- 火山录音识别任务；不保存音频链接或凭据。
CREATE TABLE IF NOT EXISTS mf_transcription_task (
 id CHAR(36) NOT NULL PRIMARY KEY COMMENT '转写任务 ID，也是火山请求 ID',
 meeting_id CHAR(36) NOT NULL COMMENT '所属会议 ID',
 recording_id CHAR(36) NULL COMMENT '来源录音 ID；链接转写时为空',
 created_by BIGINT NOT NULL COMMENT '提交人用户 ID',
 status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举',
 segments_json MEDIUMTEXT NULL COMMENT '识别片段 JSON，属于敏感会议内容',
 raw_response LONGTEXT NULL COMMENT '火山查询完成时的原始响应正文，属于敏感会议内容',
 speaker_review_json LONGTEXT NULL COMMENT '说话人逐句校对快照 JSON，属于敏感会议内容',
 speaker_review_version INT NOT NULL DEFAULT 0 COMMENT '说话人校对快照版本号，用于并发控制',
 next_poll_at DATETIME NULL COMMENT '下一次允许自动查询的时间',
 lease_token CHAR(36) NULL COMMENT '轮询任务租约标识',
 lease_until DATETIME NULL COMMENT '轮询任务租约截止时间',
 poll_count INT NOT NULL DEFAULT 0 COMMENT '自动查询次数',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '任务更新时间',
 INDEX idx_transcription_meeting (meeting_id, created_at),
 INDEX idx_transcription_recording (recording_id, created_at),
 INDEX idx_transcription_poll (status, next_poll_at, lease_until),
 CONSTRAINT fk_transcription_meeting FOREIGN KEY (meeting_id) REFERENCES mf_meeting(id),
 CONSTRAINT fk_transcription_user FOREIGN KEY (created_by) REFERENCES mf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议录音转写任务表';
