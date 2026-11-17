package com.ylz.futuremade.assistant.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;
/** 天气接口的当前天气和单位；天气字段由配置的外部接口决定。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherForecastResponse {
    /** 地点对应的时区名称。 */
    private String timezone;
    /** 当前天气数据，字段随天气请求配置变化。 */
    private Map<String, Object> current;
    /** 当前天气字段的单位，字段随天气请求配置变化。 */
    @JsonProperty("current_units") private Map<String, Object> currentUnits;
}
