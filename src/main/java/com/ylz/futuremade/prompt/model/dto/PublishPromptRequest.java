package com.ylz.futuremade.prompt.model.dto;

import lombok.Getter;
import lombok.Setter;
/** 发布或回滚提示词请求。 */
@Getter @Setter
public class PublishPromptRequest {
    /** 目标版本 ID。 */ private Long versionId;
    /** 操作前看到的生效版本 ID，用于并发校验。 */ private Long expectedActiveVersionId;
    /** 发布或回滚原因。 */ private String reason;
}
