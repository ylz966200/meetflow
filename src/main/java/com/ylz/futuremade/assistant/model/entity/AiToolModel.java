package com.ylz.futuremade.assistant.model.entity;

import lombok.Getter;
import lombok.Setter;

/** AI 工具配置表（ai_tool）对应的数据模型。 */
@Getter
@Setter
public class AiToolModel {
    /** 工具名称，也是工具唯一标识。 */
    private String toolName;

    /** 是否启用工具。 */
    private Boolean enabled;

}
