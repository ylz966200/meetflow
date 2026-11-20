-- 已有知识问答评测表执行一次。
ALTER TABLE mf_knowledge_evaluation_run
 ADD COLUMN manual_review_status SMALLINT NOT NULL DEFAULT 0 COMMENT '人工复核状态，0未复核、1通过、2不通过',
 ADD COLUMN manual_review_note VARCHAR(1000) NULL COMMENT '人工复核备注';
