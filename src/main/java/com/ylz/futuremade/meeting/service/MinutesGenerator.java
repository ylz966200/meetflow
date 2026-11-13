package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import org.springframework.stereotype.Component;
import com.ylz.futuremade.prompt.service.PromptService;

import java.util.List;
import java.util.Map;

/**
 * 根据文字稿和已确认大纲生成待审核纪要。
 */
@Component
public class MinutesGenerator {
    private final AiModelGateway model;
    private final ObjectMapper json;
    private final PromptService prompts;

    public MinutesGenerator(AiModelGateway model, ObjectMapper json, PromptService prompts) {
        this.model = model;
        this.json = json;
        this.prompts = prompts;
    }

    public String generate(String transcript, List<String> outline, String systemPrompt, long callId) {
        try {
            String data = json.writeValueAsString(Map.of("confirmedOutline", outline, "transcript", transcript));
            prompts.recordInput(callId, systemPrompt, data);
            var modelResponse = model.call(systemPrompt, data);
            String response = modelResponse.getResult().getOutput().getText();
            prompts.recordOutput(callId, response);
            prompts.recordMetadata(callId, modelResponse);
            if (response == null || response.isBlank() || response.length() > 20000)
                throw new IllegalArgumentException("纪要内容无效");
            return response.strip();
        } catch (Exception ex) {
            throw new MinutesGenerationException(ex);
        }
    }

    /**
     * 模型调用或输出格式无效。
     */
    public static class MinutesGenerationException extends RuntimeException {
        public MinutesGenerationException(Throwable cause) {
            super(cause);
        }
    }
}
