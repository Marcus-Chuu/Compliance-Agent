package com.nbcb.assistagent.rag.split;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagChunkingProperties.class)
public class RagChunkConfig {

    @Bean
    public TokenTextSplitter tokenTextSplitter(RagChunkingProperties properties) {

        return TokenTextSplitter.builder()
                .withChunkSize(properties.getMaxTokens())
                .withMinChunkSizeChars(
                        properties.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(
                        properties.getMinChunkLengthToEmbed())
                .withMaxNumChunks(
                        properties.getMaxNumChunks())
                .withKeepSeparator(
                        properties.isKeepSeparator())
                .withPunctuationMarks(
                        List.of('。', '；', '？', '！', '\n'))
                .build();
    }


}
