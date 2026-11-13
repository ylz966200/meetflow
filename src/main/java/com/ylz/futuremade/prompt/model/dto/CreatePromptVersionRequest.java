package com.ylz.futuremade.prompt.model.dto;

import lombok.Getter;
import lombok.Setter;
/** 创建提示词版本请求。 */
@Getter @Setter
public class CreatePromptVersionRequest {
    /** 提示词内容，最多 10000 字符。 */ private String content;
    /** 修改原因，最多 500 字符。 */ private String changeNote;
}
