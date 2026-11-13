-- 停止旧版本应用并备份后执行一次；历史上仅能恢复录音当前关联的任务。
ALTER TABLE mf_transcription_task ADD COLUMN recording_id CHAR(36) NULL COMMENT '来源录音 ID；链接转写时为空' AFTER meeting_id;
CREATE INDEX idx_transcription_recording ON mf_transcription_task(recording_id,created_at);
UPDATE mf_transcription_task t JOIN mf_recording r ON r.transcription_task_id=t.id AND r.meeting_id=t.meeting_id SET t.recording_id=r.id WHERE t.recording_id IS NULL;
