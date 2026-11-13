-- 对旧转写任务表执行一次；已有任务可在服务启动后恢复查询。
ALTER TABLE mf_transcription_task ADD COLUMN next_poll_at DATETIME NULL COMMENT '下一次允许自动查询的时间';
ALTER TABLE mf_transcription_task ADD COLUMN lease_token CHAR(36) NULL COMMENT '轮询任务租约标识';
ALTER TABLE mf_transcription_task ADD COLUMN lease_until DATETIME NULL COMMENT '轮询任务租约截止时间';
ALTER TABLE mf_transcription_task ADD COLUMN poll_count INT NOT NULL DEFAULT 0 COMMENT '自动查询次数';
CREATE INDEX idx_transcription_poll ON mf_transcription_task(status,next_poll_at,lease_until);
