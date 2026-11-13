-- 现有数据库执行一次；历史任务尚无人工校对，版本从 0 开始。
ALTER TABLE mf_transcription_task
  ADD COLUMN speaker_review_json LONGTEXT NULL COMMENT '说话人逐句校对快照 JSON，属于敏感会议内容' AFTER raw_response,
  ADD COLUMN speaker_review_version INT NOT NULL DEFAULT 0 COMMENT '说话人校对快照版本号，用于并发控制' AFTER speaker_review_json;
