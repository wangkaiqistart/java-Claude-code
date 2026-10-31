package com.example.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    ChatClient defaultChatClient(ChatClient.Builder builder){
        return builder.build();

    }
    @Bean
    ChatClient customChatClient(ChatClient.Builder builder){
        return builder

                .defaultSystem(
                s->s.text("You are a helpful assistant.你叫 小猫")
                        .metadata("sessionId", "default-session")
        ).build();
    }
    @Bean
    ChatClient deepSeekChatClient(DeepSeekChatModel chatModel) {
        return ChatClient.create(chatModel);
    }


}
