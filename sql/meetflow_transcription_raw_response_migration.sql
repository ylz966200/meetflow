-- 现有数据库执行一次；历史任务的原始响应无法自动补全，保留为空。
ALTER TABLE mf_transcription_task
    ADD COLUMN raw_response LONGTEXT NULL COMMENT '火山查询完成时的原始响应正文，属于敏感会议内容' AFTER segments_json;
