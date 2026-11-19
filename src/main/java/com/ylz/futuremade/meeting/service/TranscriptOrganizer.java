package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.ylz.futuremade.assistant.service.AiModelGateway;
import org.springframework.stereotype.Component;

/**
 * 调用模型整理未经信任的转写数据，并记录实际输入输出。
 */
@Component
public class TranscriptOrganizer {
    private final AiModelGateway model;

    public TranscriptOrganizer(AiModelGateway model) {
        this.model = model;
    }

    public String generate(String content, AiCallContext audit) {
        var response = model.call(audit, content);
        String output = response.getResult().getOutput().getText();
        if (output == null || output.isBlank() || output.length() > 200000)
            throw new IllegalArgumentException("文字稿整理结果无效");
        return output.strip();
    }
}
