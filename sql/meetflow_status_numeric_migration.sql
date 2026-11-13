-- 必须先停止旧版本应用并备份数据库。MySQL DDL 会隐式提交，迁移失败需按备份恢复。
-- 执行前核对六张表状态值均为文档列出的英文值，且不存在未知值；此脚本只能执行一次。
-- 业务状态码：1草稿、2确认、3执行中、4成功、5失败、6处理中、7待确认、8提交中、9已上传、10上传中、11已提交。
UPDATE mf_outline_version SET status=CASE status WHEN 'DRAFT' THEN '1' WHEN 'CONFIRMED' THEN '2' ELSE status END;
UPDATE mf_minutes_version SET status=CASE status WHEN 'DRAFT' THEN '1' WHEN 'CONFIRMED' THEN '2' ELSE status END;
UPDATE mf_ai_call SET status=CASE status WHEN 'RUNNING' THEN '3' WHEN 'SUCCEEDED' THEN '4' WHEN 'FAILED' THEN '5' ELSE status END;
UPDATE mf_generation_request SET status=CASE status WHEN 'RUNNING' THEN '3' WHEN 'SUCCEEDED' THEN '4' WHEN 'FAILED' THEN '5' ELSE status END;
UPDATE mf_transcription_task SET status=CASE status WHEN 'SUBMITTING' THEN '8' WHEN 'PROCESSING' THEN '6' WHEN 'UNKNOWN' THEN '7' WHEN 'SUCCEEDED' THEN '4' WHEN 'FAILED' THEN '5' ELSE status END;
UPDATE mf_recording SET status=CASE status WHEN 'UPLOADING' THEN '10' WHEN 'UPLOADED' THEN '9' WHEN 'SUBMITTING' THEN '8' WHEN 'SUBMITTED' THEN '11' WHEN 'FAILED' THEN '5' ELSE status END;
ALTER TABLE mf_outline_version MODIFY COLUMN status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举';
ALTER TABLE mf_minutes_version MODIFY COLUMN status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举';
ALTER TABLE mf_ai_call MODIFY COLUMN status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举';
ALTER TABLE mf_generation_request MODIFY COLUMN status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举';
ALTER TABLE mf_transcription_task MODIFY COLUMN status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举';
ALTER TABLE mf_recording MODIFY COLUMN status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举';
