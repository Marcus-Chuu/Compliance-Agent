package com.nbcb.assistagent.controller;

import com.nbcb.assistagent.app.AssistantApp;
import com.nbcb.assistagent.security.PromptInjectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/assist")
@Slf4j
public class AssistController {

    private static final int MAX_MESSAGE_LENGTH = 8_000;

    private static final int MAX_CHAT_ID_LENGTH = 128;

    private final AssistantApp assistantApp;

    public AssistController(AssistantApp assistantApp) {
        this.assistantApp = assistantApp;
    }

    @PostMapping(
            value = "/chat/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(@RequestBody ChatStreamRequest request) {
        String message = validateMessage(request == null ? null : request.message());
        String chatId = normalizeChatId(request == null ? null : request.chatId());
        AtomicLong eventId = new AtomicLong();

        Flux<ServerSentEvent<ChatStreamEvent>> contentEvents = assistantApp
                .doChatByStream(message, chatId)
                .map(content -> event(
                        eventId.incrementAndGet(),
                        "message",
                        new ChatStreamEvent("message", content, chatId)));

        Flux<ServerSentEvent<ChatStreamEvent>> completedEvent = Flux.defer(() -> Flux.just(event(
                eventId.incrementAndGet(),
                "done",
                new ChatStreamEvent("done", "", chatId))));

        return contentEvents
                .concatWith(completedEvent)
                .onErrorResume(PromptInjectionException.class, exception -> {
                    log.warn("已拦截疑似提示词注入请求: chatId={}", chatId);
                    return Flux.just(event(
                            eventId.incrementAndGet(),
                            "error",
                            new ChatStreamEvent("error", exception.getMessage(), chatId)));
                })
                .onErrorResume(exception -> {
                    log.error("流式问答失败: chatId={}", chatId, exception);
                    return Flux.just(event(
                            eventId.incrementAndGet(),
                            "error",
                            new ChatStreamEvent("error", "回答生成失败，请稍后重试。", chatId)));
                });
    }

    private ServerSentEvent<ChatStreamEvent> event(long id, String event, ChatStreamEvent data) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .id(Long.toString(id))
                .event(event)
                .data(data)
                .build();
    }

    private String validateMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new ResponseStatusException(BAD_REQUEST, "message 不能为空");
        }
        String normalized = message.strip();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "message 长度不能超过 " + MAX_MESSAGE_LENGTH);
        }
        return normalized;
    }

    private String normalizeChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return UUID.randomUUID().toString();
        }
        String normalized = chatId.strip();
        if (normalized.length() > MAX_CHAT_ID_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "chatId 长度不能超过 " + MAX_CHAT_ID_LENGTH);
        }
        return normalized;
    }

    public record ChatStreamRequest(String message, String chatId) {
    }

    public record ChatStreamEvent(String type, String content, String chatId) {
    }
}
