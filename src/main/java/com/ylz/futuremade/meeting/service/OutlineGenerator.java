package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.model.dto.OutlineModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import org.springframework.stereotype.Component;
import com.ylz.futuremade.prompt.service.PromptService;

import java.util.ArrayList;
import java.util.List;

/**
 * 将文字稿作为不可信数据，生成待人工审核的大纲。
 */
@Component
public class OutlineGenerator {
    private final AiModelGateway model;
    private final ObjectMapper json;
    private final PromptService prompts;

    public OutlineGenerator(AiModelGateway model, ObjectMapper json, PromptService prompts) {
        this.model = model;
        this.json = json;
        this.prompts = prompts;
    }

    public List<String> generate(String transcript, String systemPrompt, long callId) {
        try {
            prompts.recordInput(callId, systemPrompt, transcript);
            var modelResponse = model.call(systemPrompt, transcript);
            String response = modelResponse.getResult().getOutput().getText();
            prompts.recordOutput(callId, response);
            prompts.recordMetadata(callId, modelResponse);
            var parsed = json.readValue(response, OutlineModelResponse.class);
            var items = parsed.getItems();
            if (items == null || items.isEmpty() || items.size() > 30)
                throw new IllegalArgumentException("大纲格式无效");
            List<String> titles = new ArrayList<>();
            for (String item : items) {
                if (item == null || item.isBlank() || item.length() > 200)
                    throw new IllegalArgumentException("大纲条目无效");
                titles.add(item.strip());
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
