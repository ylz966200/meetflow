package com.ylz.futuremade.assistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
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

    public WeatherTools(WeatherConfigMapper configMapper) {
        this.configMapper = configMapper;
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
            JsonNode geo = client.get().uri(geocodingUrl, location.trim())
                    .retrieve().body(JsonNode.class);
            JsonNode results = geo == null ? null : geo.path("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return "未找到该地点，请提供更具体的城市名称。";
            }
            JsonNode place = results.get(0);
            if (!place.hasNonNull("latitude") || !place.hasNonNull("longitude")) {
                return "地点查询未返回有效坐标，请换一个地点名称。";
            }
            double lat = place.path("latitude").asDouble();
            double lon = place.path("longitude").asDouble();
            JsonNode forecast = client.get().uri(forecastUrl, lat, lon)
                    .retrieve().body(JsonNode.class);
            if (forecast == null || !forecast.hasNonNull("current")) {
                return "天气服务未返回当前天气，请稍后重试。";
            }
            String json="匹配地点：" + place.path("name").asText() + "，" + place.path("admin1").asText("") + "，" + place.path("country").asText("")
                    + "；纬度：" + lat + "，经度：" + lon + "；时区：" + forecast.path("timezone").asText()
                    + "；当前天气数据：" + forecast.path("current") + "；单位：" + forecast.path("current_units")
                    + "。天气代码采用 WMO 标准；若同名地点可能有歧义，请向用户说明所匹配的地点。";
            return json;
        } catch (DataAccessException ex) {
            return "读取天气工具配置失败，请稍后重试。";
        } catch (RestClientException | IllegalArgumentException ex) {
            return "地点或天气服务暂时不可用，请稍后重试。";
        }
    }
}


