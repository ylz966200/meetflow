package com.ylz.futuremade.assistant.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 统一选择并调用已注册的聊天模型。
 */
@Component
public class AiModelGateway {
    private final Map<String, ChatModel> models;
    private final String defaultName;
    private final AiCallAdvisor advisor;

    public AiModelGateway(Map<String, ChatModel> models, @Value("${meetflow.ai.default-model:deepSeekChatModel}") String defaultName, AiCallAdvisor advisor) {
        this.models = Map.copyOf(models);
        this.defaultName = defaultName;
        this.advisor = advisor;
    }

    public ChatModel model(String name) {
        ChatModel model = models.get(name);
        if (model == null) throw new IllegalArgumentException("未配置模型: " + name);
        return model;
    }

    public ChatResponse call(AiCallContext context, String input) {
        return call(defaultName, context, input);
    }

    /** 为需要自行构造请求的 Spring AI 组件提供带调用记录的客户端。 */
    public ChatClient.Builder clientBuilder(AiCallContext context) {
        return ChatClient.builder(model(defaultName))
                .defaultOptions(org.springframework.ai.chat.prompt.ChatOptions.builder().temperature(0.0).build())
                .defaultAdvisors(spec -> spec.advisors(advisor).param(AiCallAdvisor.CONTEXT, context)
                        .param(AiCallAdvisor.PROVIDER, defaultName));
    }

    public ChatResponse call(String modelName, AiCallContext context, String input) {
        var prompt = new Prompt(List.of(new SystemMessage(context.getPrompt().getContent()), new UserMessage(input)));
        return call(modelName, context, prompt);
    }

    public ChatResponse call(AiCallContext context, Prompt prompt) {
        return call(defaultName, context, prompt);
    }

    private ChatResponse call(String modelName, AiCallContext context, Prompt prompt) {
        return ChatClient.create(model(modelName)).prompt(prompt)
                .advisors(spec -> spec.advisors(advisor).param(AiCallAdvisor.CONTEXT, context)
                        .param(AiCallAdvisor.PROVIDER, modelName))
                .call().chatResponse();
    }
}
