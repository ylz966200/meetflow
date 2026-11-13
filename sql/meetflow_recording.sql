CREATE TABLE IF NOT EXISTS mf_recording (
 id CHAR(36) NOT NULL PRIMARY KEY COMMENT '录音文件唯一标识',
 meeting_id CHAR(36) NOT NULL COMMENT '所属会议 ID',
 object_key VARCHAR(255) NOT NULL COMMENT 'TOS 对象 Key，不包含签名链接',
 original_name VARCHAR(255) NOT NULL COMMENT '用户上传的原始文件名',
 file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
 format VARCHAR(8) NOT NULL COMMENT '音频容器格式',
 status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举',
 transcription_task_id CHAR(36) NULL COMMENT '关联的识别任务 ID',
 created_by BIGINT NOT NULL COMMENT '上传人用户 ID',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录音记录创建时间',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '录音记录更新时间',
 INDEX idx_recording_meeting (meeting_id,created_at),
 CONSTRAINT fk_recording_meeting FOREIGN KEY (meeting_id) REFERENCES mf_meeting(id),
 CONSTRAINT fk_recording_user FOREIGN KEY (created_by) REFERENCES mf_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议录音文件表';
