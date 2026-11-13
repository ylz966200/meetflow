-- 历史脚本：仅适用于状态仍为英文字符串的旧结构；数字状态迁移后不得执行。
-- 已有录音表增加 SUBMITTING 状态说明，保留现有字段类型和约束。
ALTER TABLE mf_recording MODIFY COLUMN status VARCHAR(24) NOT NULL COMMENT 'UPLOADING、UPLOADED、SUBMITTING、SUBMITTED 或 FAILED';
