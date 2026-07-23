package com.nbcb.assistagent.rag.split;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "assist.rag.chunking")
public class RagChunkingProperties {

    private String strategy = "markdown-article";

    private int maxTokens = 500;

    private int minChunkSizeChars = 100;

    private int minChunkLengthToEmbed = 20;

    private int maxNumChunks = 100;

    private boolean keepSeparator = true;

}
