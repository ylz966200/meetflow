package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;

/**
 * 调用模型整理未经信任的转写数据，并记录实际输入输出。
 */
@Component
public class TranscriptOrganizer {
    private final DeepSeekChatModel model;
    private final PromptService prompts;

    public TranscriptOrganizer(DeepSeekChatModel model, PromptService prompts) {
        this.model = model;
        this.prompts = prompts;
    }

    public String generate(String content, String systemPrompt, long callId) {
        prompts.recordInput(callId, systemPrompt, content);
        var response = ChatClient.builder(model).build().prompt().system(systemPrompt).user(content).call().chatResponse();
        String output = response.getResult().getOutput().getText();
        prompts.recordOutput(callId, output);
        prompts.recordMetadata(callId, response);
        if (output == null || output.isBlank() || output.length() > 200000)
            throw new IllegalArgumentException("文字稿整理结果无效");
        return output.strip();
    }
}
