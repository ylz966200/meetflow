package com.ylz.futuremade.assistant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface WeatherConfigMapper {
    @Select("SELECT config_key, config_value FROM weather_tool_config")
    List<WeatherConfigEntry> findAll();

    /** 天气工具的一项配置。
     * @param configKey 配置名称
     * @param configValue 配置值，可能包含敏感信息
     */
    record WeatherConfigEntry(String configKey, String configValue) {}
}
