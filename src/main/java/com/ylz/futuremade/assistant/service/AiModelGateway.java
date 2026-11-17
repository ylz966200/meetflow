package com.ylz.futuremade.assistant.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 统一选择并调用已注册的聊天模型。
 */
@Component
public class AiModelGateway {
    private final Map<String, ChatModel> models;
    private final String defaultName;

    public AiModelGateway(Map<String, ChatModel> models, @Value("${meetflow.ai.default-model:deepSeekChatModel}") String defaultName) {
        this.models = Map.copyOf(models);
        this.defaultName = defaultName;
    }

    public ChatModel model(String name) {
        ChatModel model = models.get(name);
        if (model == null) throw new IllegalArgumentException("未配置模型: " + name);
        return model;
    }

    public ChatResponse call(String system, String user) {
        return ChatClient.builder(model(defaultName)).build().prompt().system(system).user(user).call().chatResponse();
    }

    /** 使用指定模型执行对话，供评测等需要明确模型的场景复用。 */
    public ChatResponse call(String modelName, String system, String user) {
        return ChatClient.builder(model(modelName)).build().prompt().system(system).user(user).call().chatResponse();
    }

    public ChatResponse call(Prompt prompt) {
        return model(defaultName).call(prompt);
    }
}
