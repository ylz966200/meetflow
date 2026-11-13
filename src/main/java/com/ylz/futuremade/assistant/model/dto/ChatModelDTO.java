package com.ylz.futuremade.assistant.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 聊天接口接收的模型配置和用户输入。 */
@Getter
@Setter
public class ChatModelDTO {
    /** 本次调用使用的模型名称。 */
    private String modelName;
    /** 模型服务平台标识，例如 deepseek。 */
    private String platform;
    /** 模型采样温度，具体取值范围由模型服务决定。 */
    private Double temperature;
    /** 用户本次输入的聊天内容。 */
    private String info;
}


