-- 已有数据库执行一次；请勿重复执行此增量脚本。
ALTER TABLE mf_ai_call ADD COLUMN request_key CHAR(36) NULL COMMENT '关联生成请求标识，历史记录可为空';
ALTER TABLE mf_ai_call ADD COLUMN outline_version INT NULL COMMENT '纪要生成所依据的大纲版本号';
CREATE INDEX idx_ai_call_request ON mf_ai_call(request_key);
