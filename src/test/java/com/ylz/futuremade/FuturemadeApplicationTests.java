package com.ylz.futuremade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EnabledIfSystemProperty(named = "futuremade.live-model-tests", matches = "true")
class FuturemadeApplicationTests {

    @Test
    public void test111(@Autowired DeepSeekChatModel deepSeekChatModel){
        DeepSeekChatOptions deepSeekChatOptions = DeepSeekChatOptions.builder().model("deepseek-chat")
                .temperature(1.9d)
                .build();
        Prompt prompt = new Prompt("请描述一首清晨的诗");
        ChatResponse call = deepSeekChatModel.call(prompt);
        assertNotNull(call.getResult().getOutput().getText());
    }


    @Test
    public void chat(@Autowired ChatClient.Builder chatclient) {
        ChatClient build = chatclient.build();
        String info = build.prompt().user("你好").call().content();
        assertNotNull(info);
    }

}
