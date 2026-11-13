package com.ylz.futuremade.assistant.controller;

import com.ylz.futuremade.assistant.tool.AuthorizedToolRegistry;

import com.ylz.futuremade.assistant.model.dto.ChatModelDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.security.Principal;

/**
 * AI 助手对话控制器：提供已认证用户的流式对话，并按用户权限提供可调用工具。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AuthorizedToolRegistry toolRegistry;
    private Map<String, ChatModel> chatModelMap = new HashMap<>();
    @Autowired
    private ChatMemory chatMemory;

    public ChatController(DeepSeekChatModel deepSeekChatModel, AuthorizedToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        chatModelMap.put("deepseek", deepSeekChatModel);
    }

    /** 发起流式 AI 对话，并使用当前登录用户的对话记忆和工具权限。 */
    @PostMapping
    public Flux<String> chat(@RequestBody ChatModelDTO request, Principal principal) {
        ChatModel chatModel = chatModelMap.getOrDefault(request.getPlatform(), null);
        if (chatModel == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported platform");
        }        ChatClient.Builder builder = ChatClient.builder(chatModel);

        ChatClient build = builder.defaultOptions(ChatOptions.builder()
                .model(request.getModelName())
                .temperature(request.getTemperature())
                .build()).defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
        // 每个用户暂用一个独立对话；不信任请求体中的用户名和会话 ID。
        String username = principal.getName();
        String conversationId = "user:" + username;
        return build.prompt().user(request.getInfo()).toolCallbacks(toolRegistry.forUser(username))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream().content();
    }
}






