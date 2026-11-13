-- 历史脚本：仅适用于状态字段仍为英文字符串的旧结构，数字状态迁移后不得执行。
-- 依据旧数据库 SHOW CREATE TABLE 生成，仅添加或更新中文注释；执行前请核对目标数据库。
-- MySQL DDL 会隐式提交，执行前应备份，若中途失败需重新核对后续语句。

-- AI 工具配置表
ALTER TABLE `ai_tool` MODIFY COLUMN `tool_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '工具名称，也是工具唯一标识';
ALTER TABLE `ai_tool` MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用工具';
ALTER TABLE `ai_tool` COMMENT='AI 工具配置表';

-- AI 工具用户授权表
ALTER TABLE `ai_tool_user_permission` MODIFY COLUMN `username` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '获授权用户名';
ALTER TABLE `ai_tool_user_permission` MODIFY COLUMN `tool_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '获授权工具名称';
ALTER TABLE `ai_tool_user_permission` COMMENT='AI 工具用户授权表';

-- 天气工具配置表
ALTER TABLE `weather_tool_config` MODIFY COLUMN `config_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置项名称';
ALTER TABLE `weather_tool_config` MODIFY COLUMN `config_value` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置项的值';
ALTER TABLE `weather_tool_config` COMMENT='天气工具配置表';

-- 系统用户表
ALTER TABLE `mf_user` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户主键 ID';
ALTER TABLE `mf_user` MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '登录用户名，区分大小写';
ALTER TABLE `mf_user` MODIFY COLUMN `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt 密码哈希，禁止明文存储';
ALTER TABLE `mf_user` MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '账号是否启用';
ALTER TABLE `mf_user` MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间';
ALTER TABLE `mf_user` MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '账号最后更新时间';
ALTER TABLE `mf_user` COMMENT='系统用户表';

-- 系统角色表
ALTER TABLE `mf_role` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色主键 ID';
ALTER TABLE `mf_role` MODIFY COLUMN `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '系统角色编码，区分大小写';
ALTER TABLE `mf_role` MODIFY COLUMN `name` varchar(100) NOT NULL COMMENT '角色显示名称';
ALTER TABLE `mf_role` MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '角色是否启用';
ALTER TABLE `mf_role` COMMENT='系统角色表';

-- 系统用户角色关联表
ALTER TABLE `mf_user_role` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户 ID';
ALTER TABLE `mf_user_role` MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '系统角色 ID';
ALTER TABLE `mf_user_role` COMMENT='系统用户角色关联表';

-- 会议基本信息表
ALTER TABLE `mf_meeting` MODIFY COLUMN `id` char(36) NOT NULL COMMENT '会议 ID，UUID 字符串';
ALTER TABLE `mf_meeting` MODIFY COLUMN `owner_id` bigint NOT NULL COMMENT '会议组织者用户 ID';
ALTER TABLE `mf_meeting` MODIFY COLUMN `title` varchar(200) NOT NULL COMMENT '会议主题';
ALTER TABLE `mf_meeting` MODIFY COLUMN `meeting_time` datetime DEFAULT NULL COMMENT '会议时间，可为空';
ALTER TABLE `mf_meeting` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会议创建时间';
ALTER TABLE `mf_meeting` COMMENT='会议基本信息表';

-- 会议成员表
ALTER TABLE `mf_meeting_member` MODIFY COLUMN `meeting_id` char(36) NOT NULL COMMENT '会议 ID';
ALTER TABLE `mf_meeting_member` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '会议成员用户 ID';
ALTER TABLE `mf_meeting_member` MODIFY COLUMN `member_role` varchar(16) NOT NULL COMMENT '会议成员角色，编辑者或查看者';
ALTER TABLE `mf_meeting_member` COMMENT='会议成员表';

-- 会议成员变更审计表
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审计记录 ID';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `meeting_id` char(36) NOT NULL COMMENT '会议 ID';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户 ID';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `target_id` bigint NOT NULL COMMENT '被操作的成员用户 ID';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `action` varchar(20) NOT NULL COMMENT '成员变更操作类型';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `old_role` varchar(16) DEFAULT NULL COMMENT '变更前成员角色，可为空';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `new_role` varchar(16) DEFAULT NULL COMMENT '变更后成员角色，可为空';
ALTER TABLE `mf_meeting_member_audit` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间';
ALTER TABLE `mf_meeting_member_audit` COMMENT='会议成员变更审计表';

-- 会议文字稿版本表
ALTER TABLE `mf_transcript_version` MODIFY COLUMN `meeting_id` char(36) NOT NULL COMMENT '会议 ID';
ALTER TABLE `mf_transcript_version` MODIFY COLUMN `version` int NOT NULL COMMENT '文字稿版本号';
ALTER TABLE `mf_transcript_version` MODIFY COLUMN `content` mediumtext NOT NULL COMMENT '会议文字稿正文，属于敏感数据';
ALTER TABLE `mf_transcript_version` MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '文字稿版本创建人用户 ID';
ALTER TABLE `mf_transcript_version` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '文字稿版本创建时间';
ALTER TABLE `mf_transcript_version` COMMENT='会议文字稿版本表';

-- 会议大纲版本表
ALTER TABLE `mf_outline_version` MODIFY COLUMN `meeting_id` char(36) NOT NULL COMMENT '会议 ID';
ALTER TABLE `mf_outline_version` MODIFY COLUMN `version` int NOT NULL COMMENT '大纲版本号';
ALTER TABLE `mf_outline_version` MODIFY COLUMN `transcript_version` int NOT NULL COMMENT '关联的文字稿版本号';
ALTER TABLE `mf_outline_version` MODIFY COLUMN `status` varchar(16) NOT NULL COMMENT '大纲状态：DRAFT 草稿或 CONFIRMED 已确认';
ALTER TABLE `mf_outline_version` MODIFY COLUMN `items_json` text NOT NULL COMMENT '按顺序保存大纲条目的 JSON';
ALTER TABLE `mf_outline_version` MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '大纲版本创建人用户 ID';
ALTER TABLE `mf_outline_version` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '大纲版本创建时间';
ALTER TABLE `mf_outline_version` COMMENT='会议大纲版本表';

-- 提示词业务场景表
ALTER TABLE `mf_prompt_scenario` MODIFY COLUMN `code` varchar(64) NOT NULL COMMENT '业务场景编码';
ALTER TABLE `mf_prompt_scenario` MODIFY COLUMN `name` varchar(100) NOT NULL COMMENT '场景名称';
ALTER TABLE `mf_prompt_scenario` MODIFY COLUMN `active_version_id` bigint DEFAULT NULL COMMENT '当前生效的提示词版本 ID';
ALTER TABLE `mf_prompt_scenario` COMMENT='提示词业务场景表';

-- 提示词不可变版本表
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提示词版本主键 ID';
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `scenario` varchar(64) NOT NULL COMMENT '业务场景编码';
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `version` int NOT NULL COMMENT '场景内递增的版本号';
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `content` text NOT NULL COMMENT '系统提示词内容';
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `change_note` varchar(500) NOT NULL COMMENT '版本修改原因';
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `created_by` bigint DEFAULT NULL COMMENT '创建人用户 ID，系统初始化时为空';
ALTER TABLE `mf_prompt_version` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间';
ALTER TABLE `mf_prompt_version` COMMENT='提示词不可变版本表';

-- 提示词发布回滚审计表
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审计记录主键 ID';
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `scenario` varchar(64) NOT NULL COMMENT '业务场景编码';
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `old_version_id` bigint DEFAULT NULL COMMENT '发布前生效版本 ID，可为空';
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `new_version_id` bigint NOT NULL COMMENT '发布后生效版本 ID';
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人用户 ID';
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `reason` varchar(500) NOT NULL COMMENT '发布或回滚原因';
ALTER TABLE `mf_prompt_release_audit` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间';
ALTER TABLE `mf_prompt_release_audit` COMMENT='提示词发布回滚审计表';

-- AI 模型调用记录表
ALTER TABLE `mf_ai_call` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '调用记录主键 ID';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `scenario` varchar(64) NOT NULL COMMENT '业务场景编码';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `version_id` bigint NOT NULL COMMENT '调用时使用的提示词版本 ID';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `meeting_id` char(36) NOT NULL COMMENT '关联会议 ID';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `transcript_version` int NOT NULL COMMENT '关联文字稿版本号';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '发起人用户 ID';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `status` varchar(16) NOT NULL COMMENT '调用状态：RUNNING、SUCCEEDED 或 FAILED';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `duration_ms` bigint DEFAULT NULL COMMENT '模型调用耗时，单位毫秒';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `error_category` varchar(64) DEFAULT NULL COMMENT '失败分类，不包含模型返回内容';
ALTER TABLE `mf_ai_call` MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用开始时间';
ALTER TABLE `mf_ai_call` COMMENT='AI 模型调用记录表';
