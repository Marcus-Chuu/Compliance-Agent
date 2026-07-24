package com.nbcb.assistagent.app;

import com.nbcb.assistagent.advisor.MyLoggerAdvisor;
import com.nbcb.assistagent.chatMemory.FileBaseChatMemory;
import com.nbcb.assistagent.security.PromptInjectionGuard;
import com.nbcb.assistagent.tools.AssistantTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
@Slf4j
public class AssistantApp {

    private final ChatClient chatClient;

    private final PromptInjectionGuard promptInjectionGuard;

    private final QueryIntentRouter queryIntentRouter;


    @Resource
    private Advisor loadLocalRagAdvisor;


    public AssistantApp(
            ChatModel dashscopeChatModel,
            @Qualifier("complianceSystemPrompt") String complianceSystemPrompt,
            AssistantTools assistantTools,
            PromptInjectionGuard promptInjectionGuard,
            QueryIntentRouter queryIntentRouter) {
        this.promptInjectionGuard = promptInjectionGuard;
        this.queryIntentRouter = queryIntentRouter;
        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        ChatMemory chatMemory = new FileBaseChatMemory(fileDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(complianceSystemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(assistantTools)
                .build();
    }


    /**
     * Agent 调用 (非流式)
     * @param userMessage 用户输入
     * @param chatId 对话 id
     * @return String
     */
    public String doChat(String userMessage, String chatId) {
        promptInjectionGuard.validate(userMessage);
        ChatResponse chatResponse = request(userMessage, chatId)
                .call()
                .chatResponse();
        assert chatResponse != null;
        return chatResponse.getResult().getOutput().getText();
    }


    /**
     * 流式调用
     * @param userMessage 用户输入
     * @param chatId 对话 ID
     * @return Flux<String>
     */
    public Flux<String> doChatByStream(String userMessage, String chatId) {
        return Flux.defer(() -> {
            promptInjectionGuard.validate(userMessage);
            return request(userMessage, chatId)
                    .stream()
                    .content();
        });
    }

    private ChatClient.ChatClientRequestSpec request(String userMessage, String chatId) {
        QueryIntentRouter.QueryIntent intent = queryIntentRouter.route(userMessage);
        ChatClient.ChatClientRequestSpec request = chatClient
                .prompt()
                .user(userMessage)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor());

        if (intent == QueryIntentRouter.QueryIntent.POLICY) {
            request = request.advisors(loadLocalRagAdvisor);
        }
        log.debug("Prepared chat request: intent={}, ragEnabled={}",
                intent, intent == QueryIntentRouter.QueryIntent.POLICY);
        return request;
    }


}
