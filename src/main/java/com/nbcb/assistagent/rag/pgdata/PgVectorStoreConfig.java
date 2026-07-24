package com.nbcb.assistagent.rag.pgdata;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PgVectorStoreConfig {

    public static final String VECTOR_SCHEMA = "public";

    public static final String VECTOR_TABLE = "assist_vector_store";


    @Bean
    @Primary
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .schemaName(VECTOR_SCHEMA)
                .vectorTableName(VECTOR_TABLE)
                .maxDocumentBatchSize(10000)
                .build();
    }


}
