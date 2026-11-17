package com.ylz.futuremade.assistant.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 按需注册独立的火山方舟豆包模型，不更改项目默认模型。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "meetflow.ai.doubao", name = "enabled", havingValue = "true")
public class DoubaoModelConfiguration {
    /** 创建与其他供应商分离的模型客户端；密钥不得写入日志。 */
    @Bean("doubaoChatModel")
    public ChatModel doubaoChatModel(@Value("${meetflow.ai.doubao.base-url}") String baseUrl,
            @Value("${meetflow.ai.doubao.api-key}") String apiKey,
            @Value("${meetflow.ai.doubao.model}") String model) {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank())
            throw new IllegalStateException("启用豆包模型时须配置方舟地址、API Key 和模型 ID");
        var api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).completionsPath("/chat/completions").build();
        var options = OpenAiChatOptions.builder().model(model).build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }
}
