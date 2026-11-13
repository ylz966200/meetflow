-- 单段提取请求及结果；每次请求使用唯一 UUID，历史任务不自动重复调用。
CREATE TABLE IF NOT EXISTS mf_chunk_extraction (
 id CHAR(36) NOT NULL PRIMARY KEY COMMENT '请求标识及提取记录 ID',
 meeting_id CHAR(36) NOT NULL COMMENT '所属会议 ID',
 task_id CHAR(36) NOT NULL COMMENT '来源转写任务 ID',
 chunk_index INT NOT NULL COMMENT '技术分段序号，从 1 开始',
 speaker_review_version INT NOT NULL COMMENT '提取时的说话人校对版本',
 actor_id BIGINT NOT NULL COMMENT '发起人用户 ID',
 status SMALLINT NOT NULL COMMENT '处理状态，参见 BusinessStatus 枚举',
 ai_call_id BIGINT NULL COMMENT '关联 AI 调用记录 ID',
 result_json MEDIUMTEXT NULL COMMENT '候选提取结果 JSON，属于敏感会议内容',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求创建时间',
 INDEX idx_chunk_extraction_task (meeting_id,task_id,chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单个转写分段 AI 提取记录表';
