package com.ylz.futuremade.assistant.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台标识到模型 Bean 名称的映射。
 */
@Component
@ConfigurationProperties(prefix = "meetflow.ai")
public class AiModelProperties {
    /**
     * 各平台对应的 Spring 模型 Bean 名称。
     */
    private Map<String, String> platforms = new HashMap<>();

    public Map<String, String> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(Map<String, String> platforms) {
        this.platforms = platforms;
    }
}
