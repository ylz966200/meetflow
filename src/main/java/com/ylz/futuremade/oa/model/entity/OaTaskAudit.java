package com.ylz.futuremade.oa.model.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 本地 OA 待办状态变更记录。 */
@Getter @Setter
public class OaTaskAudit {
    /** 操作记录编号。 */ private String id;
    /** 待办编号。 */ private String taskId;
    /** 操作人编号。 */ private Long actorId;
    /** 操作前状态，创建时为空。 */ private Integer previousStatus;
    /** 操作后状态，参见 OaTaskStatus。 */ private Integer status;
    /** 操作时间。 */ private LocalDateTime createdAt;
}
