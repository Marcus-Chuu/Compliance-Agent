package com.nbcb.assistagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@SpringBootTest
public class AssistantAppTest {


    @Resource
    private AssistantApp assistantApp;

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮对话
        String message = "你好, 请告诉我申请授信的客户的准入条件";
        String response = assistantApp.doChat(message, chatId);
        Assertions.assertNotNull(response);
    }

    @Test
    void doChatWithStream() {
        String chatId = UUID.randomUUID().toString();
        String message = "请分五点说明申请授信客户的准入条件，每点给出简短解释。";
        List<String> chunks = new CopyOnWriteArrayList<>();
        long startedAt = System.nanoTime();

        Flux<String> response = assistantApp.doChatByStream(message, chatId)
                .doOnNext(chunk -> {
                    chunks.add(chunk);
                    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
                    System.out.printf("[%4d ms] %s", elapsedMillis, chunk);
                    System.out.flush();
                })
                .doOnComplete(() -> System.out.printf("%n流式响应完成，共收到 %d 个分片%n", chunks.size()));

        StepVerifier.create(response)
                .expectSubscription()
                .thenConsumeWhile(chunk -> true)
                .expectComplete()
                .verify(Duration.ofSeconds(90));

        Assertions.assertFalse(chunks.isEmpty(), "没有收到任何流式响应");
        Assertions.assertFalse(String.join("", chunks).isBlank(), "完整响应不能为空");
        Assertions.assertTrue(chunks.size() > 1,
                () -> "只收到 " + chunks.size() + " 个分片，未观察到流式分段效果");
    }



}
