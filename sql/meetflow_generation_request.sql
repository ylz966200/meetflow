-- AI 生成请求去重记录；同一个请求标识只能执行一次。
CREATE TABLE IF NOT EXISTS mf_generation_request (
 request_key CHAR(36) NOT NULL COMMENT '客户端生成的请求 UUID',
 scenario VARCHAR(32) NOT NULL COMMENT '生成场景编码',
 meeting_id CHAR(36) NOT NULL COMMENT '关联会议 ID',
 actor_id BIGINT NOT NULL COMMENT '发起人的用户 ID',
 source_version INT NOT NULL COMMENT '来源文字稿或大纲版本号',
 expected_version INT NOT NULL COMMENT '请求时的目标最新版本号',
 status SMALLINT NOT NULL COMMENT '业务状态码，参见 BusinessStatus 枚举',
 result_version INT NULL COMMENT '成功保存的目标版本号',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求创建时间',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态最后更新时间',
 PRIMARY KEY (request_key),
 INDEX idx_generation_meeting (meeting_id, scenario, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 生成请求去重记录表';
