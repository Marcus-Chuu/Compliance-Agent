package com.nbcb.assistagent.rag.index;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "assist.rag.indexing")
public class RagIndexingProperties {

    private boolean enabled = true;

    private String resourcePattern = "classpath:document/*.md";

    private String collection = "assist-documents";

    /**
     * 手动调整该版本可强制全部文档重新建立索引。
     */
    private String indexVersion = "v1";

    private String embeddingModel = "text-embedding-v4";

    private int embeddingDimensions = 1536;

    /**
     * DashScope Embedding API 单次最多接收 10 条文本。
     */
    private int embeddingBatchSize = 10;
}
