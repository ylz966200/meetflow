-- 已存在的数据库需执行一次；历史内容保留为原有纯文本。
ALTER TABLE mf_minutes_version ADD COLUMN structured_json MEDIUMTEXT NULL COMMENT '按大纲组织的纪要及行动项候选 JSON，历史纯文本版本为空' AFTER content;
