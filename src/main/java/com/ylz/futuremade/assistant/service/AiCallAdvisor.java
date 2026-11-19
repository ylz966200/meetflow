package com.ylz.futuremade.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 在聊天模型调用边界保存实际请求、返回和用量。
 */
@Component
@RequiredArgsConstructor
public class AiCallAdvisor implements CallAdvisor {
    static final String CONTEXT = AiCallContext.class.getName();
    static final String PROVIDER = "meetflow.modelProvider";
    private final PromptService prompts;
    private final ObjectMapper json;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var context = (AiCallContext) request.context().get(CONTEXT);
        var messages = request.prompt().getInstructions();
        String system = request.prompt().getSystemMessage().getText();
        // 单轮保留易读正文，多轮保存有序消息及工具调用和返回。
        String input = messages.size() == 2 && messages.get(0) instanceof SystemMessage
                && messages.get(1) instanceof UserMessage user ? user.getText() : serialize(messages);
        long id = prompts.startCall(context.getPrompt(), context.getMeetingId(), context.getTranscriptVersion(),
                context.getActorId(), context.getRequestKey(), context.getOutlineVersion());
        context.setLastCallId(id);
        long started = System.nanoTime();
        try {
            prompts.recordInput(id, system, input);
            var result = chain.nextCall(request);
            var response = result.chatResponse();
            AssistantMessage output = response.getResult().getOutput();
            prompts.recordOutput(id, output.hasToolCalls() ? serialize(output) : output.getText());
            prompts.recordMetadata(id, response, (String) request.context().get(PROVIDER));
            boolean truncated = response.hasFinishReasons(Set.of("length", "LENGTH"));
            boolean rewrite = PromptScenarioCode.MEETING_KNOWLEDGE_QUERY_REWRITE.code().equals(context.getPrompt().getScenario());
            if (rewrite && (truncated || output.getText() == null || output.getText().isBlank())) {
                finish(id, started, truncated ? "OUTPUT_TRUNCATED" : "EMPTY_OUTPUT");
                throw new IllegalStateException("问题改写结果为空或被截断");
            }
            finish(id, started, truncated ? "OUTPUT_TRUNCATED" : null);
            return result;
        } catch (RuntimeException ex) {
            finish(id, started, "MODEL_REQUEST_FAILED");
            throw ex;
        }
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化模型消息", ex);
        }
    }

    private void finish(long id, long started, String error) {
        var status = error == null ? BusinessStatus.SUCCEEDED : BusinessStatus.FAILED;
        prompts.finishCall(id, status.code(), (System.nanoTime() - started) / 1_000_000, error);
    }

    @Override
    public String getName() {
        return "meetflowAiCallAudit";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
