package com.nbcb.assistagent.rag.load;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AssistRagAdvisorConfig {

    @Bean
    public Advisor loadLocalRagAdvisor(@Qualifier("pgVectorStore") VectorStore vectorStore) {

        DocumentRetriever retriever =
                VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.7)
                        .topK(5)
                        .build();


        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(AssistContextualQueryAugmenterFactory.createInstance())
                .build();
    }

}
