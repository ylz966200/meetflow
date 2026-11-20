-- 多会议问答的会议 ID 以逗号分隔，单会议和历史调用为空。
ALTER TABLE mf_ai_call ADD COLUMN meeting_ids VARCHAR(1024) NULL COMMENT '多会议调用的会议ID列表，以逗号分隔';
