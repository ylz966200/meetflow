package com.ylz.futuremade.prompt.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 提示词发布回滚审计表 mf_prompt_release_audit 对应的数据模型。 */
@Getter @Setter
public class PromptRelease {
    /** 审计记录 ID。 */ private Long id;
    /** 业务场景编码。 */ private String scenario;
    /** 原生效版本 ID。 */ private Long oldVersionId;
    /** 新生效版本 ID。 */ private Long newVersionId;
    /** 操作人用户 ID。 */ private Long actorId;
    /** 操作原因。 */ private String reason;
    /** 操作时间。 */ private LocalDateTime createdAt;
}
