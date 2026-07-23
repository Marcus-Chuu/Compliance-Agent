package com.nbcb.assistagent.rag.index;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@ConditionalOnProperty(
        prefix = "assist.rag.indexing",
        name = "enabled",
        havingValue = "true")
@Slf4j
public class AssistDocumentIndexRunner implements ApplicationRunner {

    private final AssistDocumentIndexService indexService;

    public AssistDocumentIndexRunner(AssistDocumentIndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        AssistDocumentIndexService.IndexingSummary summary = indexService.syncDocuments();
        log.info(
                "RAG indexing finished: discoveredFiles={}, indexedFiles={}, skippedFiles={}, "
                        + "removedFiles={}, indexedChunks={}",
                summary.discoveredFiles(),
                summary.indexedFiles(),
                summary.skippedFiles(),
                summary.removedFiles(),
                summary.indexedChunks());
    }
}
