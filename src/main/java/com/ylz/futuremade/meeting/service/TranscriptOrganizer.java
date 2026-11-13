package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import org.springframework.stereotype.Component;

/**
 * 调用模型整理未经信任的转写数据，并记录实际输入输出。
 */
@Component
public class TranscriptOrganizer {
    private final AiModelGateway model;
    private final PromptService prompts;

    public TranscriptOrganizer(AiModelGateway model, PromptService prompts) {
        this.model = model;
        this.prompts = prompts;
    }

    public String generate(String content, String systemPrompt, long callId) {
        prompts.recordInput(callId, systemPrompt, content);
        var response = model.call(systemPrompt, content);
        String output = response.getResult().getOutput().getText();
        prompts.recordOutput(callId, output);
        prompts.recordMetadata(callId, response);
        if (output == null || output.isBlank() || output.length() > 200000)
            throw new IllegalArgumentException("文字稿整理结果无效");
        return output.strip();
    }
}
