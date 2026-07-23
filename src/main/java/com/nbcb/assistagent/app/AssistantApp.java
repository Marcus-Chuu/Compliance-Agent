package com.nbcb.assistagent.app;

import com.nbcb.assistagent.chatMemory.FileBaseChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
@Slf4j
public class AssistantApp {

    private final ChatClient chatClient;

    public AssistantApp(ChatModel dashscopeChatModel, @Qualifier("complianceSystemPrompt") String complianceSystemPrompt) {
        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        ChatMemory chatMemory = new FileBaseChatMemory(fileDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(complianceSystemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }


    public String doChat(String userMessage, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(userMessage)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        assert chatResponse != null;
        return chatResponse.getResult().getOutput().getText();
    }


}
