package com.nbcb.assistagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class PromptConfig {

    @Bean
    public String complianceSystemPrompt() {
        ClassPathResource resource = new ClassPathResource("prompt/system-prompt.txt");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("读取智能合规助手系统提示词失败", e);
        }
    }
}