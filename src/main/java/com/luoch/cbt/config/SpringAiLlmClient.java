package com.luoch.cbt.config;

import com.luoch.cbt.graph.LlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/** Production LLM boundary. Tests inject a fixed LlmClient instead. */
public final class SpringAiLlmClient implements LlmClient {

    private final ChatClient client;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.client = ChatClient.builder(chatModel).build();
    }

    @Override
    public String chat(String system, String user) {
        String content = client.prompt()
                .system(system)
                .user(user)
                .call()
                .content();
        return content == null ? "" : content;
    }
}
