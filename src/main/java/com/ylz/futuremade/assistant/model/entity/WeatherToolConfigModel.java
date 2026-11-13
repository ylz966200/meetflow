package com.ylz.futuremade.assistant.model.entity;

import lombok.Getter;
import lombok.Setter;

/** 天气工具配置表（weather_tool_config）对应的数据模型。 */
@Getter
@Setter
public class WeatherToolConfigModel {
    /** 配置项名称。 */
    private String configKey;

    /** 配置项的值。 */
    private String configValue;

}
