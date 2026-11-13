CREATE TABLE IF NOT EXISTS ai_tool (
    tool_name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY COMMENT '工具名称，也是工具唯一标识',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用工具'
) COMMENT='AI 工具配置表';
CREATE TABLE IF NOT EXISTS ai_tool_user_permission (
    username VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '获授权用户名',
    tool_name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '获授权工具名称',
    PRIMARY KEY (username, tool_name),
    FOREIGN KEY (tool_name) REFERENCES ai_tool (tool_name) ON DELETE CASCADE
) COMMENT='AI 工具用户授权表';
INSERT IGNORE INTO ai_tool (tool_name, enabled) VALUES ('currentWeather', TRUE);
