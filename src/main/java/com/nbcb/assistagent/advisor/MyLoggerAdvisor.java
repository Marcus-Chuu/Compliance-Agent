package com.nbcb.assistagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {


    @Override
    @NotNull
    public String getName() {
        return this.getClass().getName();
    }


    @Override
    public int getOrder() {
        return 0;
    }


    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, @NotNull CallAdvisorChain callAdvisorChain) {
        this.before(chatClientRequest);
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        this.observeAfter(response);
        return response;
    }



    @NotNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, @NotNull StreamAdvisorChain streamAdvisorChain) {
        this.before(chatClientRequest);
        return streamAdvisorChain.nextStream(chatClientRequest).doOnNext(this::observeAfter);
    }


    /**
     * 打印请求模型前的日志
     * @param request 请求
     */
    private void before(ChatClientRequest request) {
        log.info("AI Request : {}", request.prompt().getUserMessage().getText());
    }


    private void observeAfter(ChatClientResponse response) {
        if (response.chatResponse() != null) {
            log.info("AI Response : {}", response.chatResponse().getResult().getOutput().getText());
        }
    }


}
