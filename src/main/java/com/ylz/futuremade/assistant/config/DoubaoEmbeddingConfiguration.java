package com.ylz.futuremade.assistant.config;

import com.ylz.futuremade.assistant.service.ArkMultimodalEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * 按需注册方舟多模态向量模型，与聊天模型共用方舟地址和凭证。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "meetflow.ai.doubao.embedding", name = "enabled", havingValue = "true")
public class DoubaoEmbeddingConfiguration {
    /**
     * 多模态向量接口使用独立模型或推理接入点 ID，不沿用聊天模型 ID。
     */
    @Bean("doubaoEmbeddingModel")
    public EmbeddingModel doubaoEmbeddingModel(@Value("${meetflow.ai.doubao.base-url}") String baseUrl,
                                               @Value("${meetflow.ai.doubao.api-key}") String apiKey,
                                               @Value("${meetflow.ai.doubao.embedding.model}") String model) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank() || model == null || model.isBlank())
            throw new IllegalStateException("启用豆包向量时须配置方舟地址、API Key 和向量接入点 ID");
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(60));
        var client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBearerAuth(apiKey)).build();
        return new ArkMultimodalEmbeddingModel(client, model);
    }
}
