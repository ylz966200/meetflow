package com.ylz.futuremade.assistant.model.entity;

import lombok.Getter;
import lombok.Setter;

/** AI 工具用户授权表（ai_tool_user_permission）对应的数据模型。 */
@Getter
@Setter
public class AiToolUserPermissionModel {
    /** 获授权用户名。 */
    private String username;

    /** 获授权工具名称。 */
    private String toolName;

}
