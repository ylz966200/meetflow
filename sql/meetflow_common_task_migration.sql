-- 扩展既有后台任务表：索引任务不需要生成提示词或预期生成结果版本。
ALTER TABLE mf_ai_generation_task
 MODIFY prompt_version_id BIGINT NULL COMMENT '生成任务固定使用的提示词版本ID；索引任务为空',
 MODIFY expected_result_version INT NULL COMMENT '生成任务创建时的结果版本；索引任务为空';
