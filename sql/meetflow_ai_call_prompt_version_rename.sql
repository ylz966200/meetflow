-- 将调用记录的提示词版本主键字段改名；已有数据和索引会保留。
-- 仅用于存在 version_id 且尚无 prompt_version_id 的旧数据库。
ALTER TABLE mf_ai_call CHANGE COLUMN version_id prompt_version_id BIGINT NOT NULL COMMENT '调用时使用的提示词版本主键，关联 mf_prompt_version.id';
