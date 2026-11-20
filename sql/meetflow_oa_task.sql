CREATE TABLE IF NOT EXISTS mf_oa_task (
 id VARCHAR(36) NOT NULL COMMENT '本地待办唯一编号',
 meeting_id VARCHAR(36) NOT NULL COMMENT '来源会议编号',
 minutes_version INT NOT NULL COMMENT '来源纪要版本',
 action_index INT NOT NULL COMMENT '行动项序号，从零开始',
 content TEXT NOT NULL COMMENT '创建时经确认的待办内容',
 assignee_id BIGINT NOT NULL COMMENT '负责人系统用户编号',
 due_date DATE NOT NULL COMMENT '用户确认的截止日期',
 status INT NOT NULL COMMENT '待办状态：1待处理、2进行中、3已完成、4已取消',
 created_by BIGINT NOT NULL COMMENT '创建人用户编号',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
 PRIMARY KEY (id),
 UNIQUE KEY uk_oa_task_source (meeting_id,minutes_version,action_index),
 KEY idx_oa_task_assignee (assignee_id,status),
 KEY idx_oa_task_meeting (meeting_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议行动项生成的本地 OA 待办';

CREATE TABLE IF NOT EXISTS mf_oa_task_audit (
 id VARCHAR(36) NOT NULL COMMENT '操作记录编号',
 task_id VARCHAR(36) NOT NULL COMMENT '本地待办编号',
 actor_id BIGINT NOT NULL COMMENT '执行操作的用户编号',
 previous_status INT NULL COMMENT '操作前状态，创建时为空',
 status INT NOT NULL COMMENT '操作后状态：1待处理、2进行中、3已完成、4已取消',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
 PRIMARY KEY (id),
 KEY idx_oa_audit_task (task_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地 OA 待办状态变更记录';
