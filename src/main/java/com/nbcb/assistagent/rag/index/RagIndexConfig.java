package com.nbcb.assistagent.rag.index;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagIndexingProperties.class)
public class RagIndexConfig {
}
