package com.ylz.futuremade.assistant.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 地点查询接口返回的候选地点。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherLocationResponse {
    /** 地名匹配结果，未匹配时可能为空。 */
    private List<WeatherLocation> results;
}
