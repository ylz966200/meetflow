-- 已有数据库执行一次，历史记录的用量未知，保持 NULL。
ALTER TABLE mf_ai_call ADD COLUMN provider VARCHAR(32) NULL COMMENT '模型供应商';
ALTER TABLE mf_ai_call ADD COLUMN model_name VARCHAR(128) NULL COMMENT '供应商返回的模型名称';
ALTER TABLE mf_ai_call ADD COLUMN provider_request_id VARCHAR(128) NULL COMMENT '供应商返回的请求 ID';
ALTER TABLE mf_ai_call ADD COLUMN input_tokens INT NULL COMMENT '供应商返回的输入 Token 数';
ALTER TABLE mf_ai_call ADD COLUMN output_tokens INT NULL COMMENT '供应商返回的输出 Token 数';
ALTER TABLE mf_ai_call ADD COLUMN cached_tokens INT NULL COMMENT '供应商返回的缓存命中 Token 数';
