package com.ylz.futuremade.assistant.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.model.dto.WeatherForecastResponse;
import com.ylz.futuremade.assistant.model.dto.WeatherLocationResponse;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import com.ylz.futuremade.assistant.mapper.WeatherConfigMapper;
import org.springframework.dao.DataAccessException;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class WeatherTools {
    private final RestClient client;
    private final WeatherConfigMapper configMapper;
    private final ObjectMapper json;

    public WeatherTools(WeatherConfigMapper configMapper, ObjectMapper json) {
        this.configMapper = configMapper;
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Tool(description = "查询指定城市或地点当前天气。将用户输入提取为地名；只查询当前天气，不用于未来天气预报。返回匹配地点、经纬度、当地时间及天气数据。如果地点不明确，应请用户补充。")
    public String currentWeather(@ToolParam(description = "城市或地点名称，例如北京、上海或 London；不要包含天气问题") String location) {
        if (location == null || location.isBlank() || location.trim().length() < 2) {
            return "地点名称不完整，请提供至少两个字符的城市或地点名称。";
        }
        try {
            Map<String, String> config = new java.util.HashMap<>();
            for (var entry : configMapper.findAll()) {
                config.put(entry.configKey(), entry.configValue());
            }
            if (!"true".equalsIgnoreCase(config.get("enabled"))) return "天气工具当前未启用。";
            String geocodingUrl = config.get("geocoding_url");
            String forecastUrl = config.get("forecast_url");
            if (geocodingUrl == null || forecastUrl == null) return "天气工具配置不完整，请联系管理员。";
            var geo = client.get().uri(geocodingUrl, location.trim())
                    .retrieve().body(WeatherLocationResponse.class);
            if (geo == null || geo.getResults() == null || geo.getResults().isEmpty()) {
                return "未找到该地点，请提供更具体的城市名称。";
            }
            var place = geo.getResults().get(0);
            if (place == null || place.getLatitude() == null || place.getLongitude() == null) {
                return "地点查询未返回有效坐标，请换一个地点名称。";
            }
            double lat = place.getLatitude();
            double lon = place.getLongitude();
            var forecast = client.get().uri(forecastUrl, lat, lon)
                    .retrieve().body(WeatherForecastResponse.class);
            if (forecast == null || forecast.getCurrent() == null) {
                return "天气服务未返回当前天气，请稍后重试。";
            }
            String current = json.writeValueAsString(forecast.getCurrent());
            String units = forecast.getCurrentUnits() == null ? "" : json.writeValueAsString(forecast.getCurrentUnits());
            return "匹配地点：" + Objects.toString(place.getName(), "") + "，" + Objects.toString(place.getAdmin1(), "") + "，" + Objects.toString(place.getCountry(), "")
                    + "；纬度：" + lat + "，经度：" + lon + "；时区：" + Objects.toString(forecast.getTimezone(), "")
                    + "；当前天气数据：" + current + "；单位：" + units
                    + "。天气代码采用 WMO 标准；若同名地点可能有歧义，请向用户说明所匹配的地点。";
        } catch (DataAccessException ex) {
            return "读取天气工具配置失败，请稍后重试。";
        } catch (RestClientException | IllegalArgumentException | JsonProcessingException ex) {
            return "地点或天气服务暂时不可用，请稍后重试。";
        }
    }
}


