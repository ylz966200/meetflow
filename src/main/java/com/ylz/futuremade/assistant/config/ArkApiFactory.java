package com.ylz.futuremade.assistant.config;

import org.springframework.ai.openai.api.OpenAiApi;

/** 创建火山方舟 OpenAI 兼容客户端。 */
public final class ArkApiFactory {
    private ArkApiFactory() { }
    /** 按方舟 API 路径创建客户端。 */
    public static OpenAiApi create(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("请配置火山方舟地址和 API Key");
        return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey)
                .completionsPath("/chat/completions").embeddingsPath("/embeddings").build();
    }
}
