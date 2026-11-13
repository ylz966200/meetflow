package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;
import com.ylz.futuremade.prompt.service.PromptService;

import java.util.ArrayList;
import java.util.List;

/**
 * 将文字稿作为不可信数据，生成待人工审核的大纲。
 */
@Component
public class OutlineGenerator {
    private final DeepSeekChatModel model;
    private final ObjectMapper json;
    private final PromptService prompts;

    public OutlineGenerator(DeepSeekChatModel model, ObjectMapper json, PromptService prompts) {
        this.model = model;
        this.json = json;
        this.prompts = prompts;
    }

    public List<String> generate(String transcript, String systemPrompt, long callId) {
        try {
            prompts.recordInput(callId, systemPrompt, transcript);
            var modelResponse = ChatClient.builder(model).build().prompt()
                    .system(systemPrompt)
                    .user(transcript).call().chatResponse();
            String response = modelResponse.getResult().getOutput().getText();
            prompts.recordOutput(callId, response);
            prompts.recordMetadata(callId, modelResponse);
            JsonNode root = json.readTree(response);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty() || items.size() > 30)
                throw new IllegalArgumentException("大纲格式无效");
            List<String> titles = new ArrayList<>();
            for (JsonNode item : items) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 200)
                    throw new IllegalArgumentException("大纲条目无效");
                titles.add(item.asText().strip());
            }
            return List.copyOf(titles);
        } catch (Exception ex) {
            throw new OutlineGenerationException(ex);
        }
    }

    public static class OutlineGenerationException extends RuntimeException {
        public OutlineGenerationException(Throwable cause) {
            super(cause);
        }
    }
}
