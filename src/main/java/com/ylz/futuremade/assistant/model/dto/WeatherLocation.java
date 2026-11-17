package com.ylz.futuremade.assistant.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
/** 天气地点查询得到的坐标和行政区划。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherLocation {
    /** 地点名称。 */
    private String name;
    /** 地点所属一级行政区。 */
    private String admin1;
    /** 地点所属国家。 */
    private String country;
    /** 纬度，单位度。 */
    private Double latitude;
    /** 经度，单位度。 */
    private Double longitude;
}
