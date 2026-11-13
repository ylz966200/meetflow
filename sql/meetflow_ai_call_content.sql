-- 调用正文与列表元数据分开存储；包含敏感会议数据，仅授权管理员可读取。
CREATE TABLE IF NOT EXISTS mf_ai_call_content (
 call_id BIGINT NOT NULL PRIMARY KEY COMMENT '关联的 AI 调用记录 ID',
 system_content MEDIUMTEXT NOT NULL COMMENT '发送给模型的系统提示词原文',
 user_content MEDIUMTEXT NOT NULL COMMENT '发送给模型的用户消息原文，可能包含会议敏感信息',
 output_content MEDIUMTEXT NULL COMMENT '模型返回的原始文本，未返回时为空',
 tool_trace LONGTEXT NULL COMMENT '上下文工具调用和返回结果 JSON，属于敏感会议内容',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '输入内容记录时间',
 CONSTRAINT fk_call_content_call FOREIGN KEY (call_id) REFERENCES mf_ai_call(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用输入输出内容表';
