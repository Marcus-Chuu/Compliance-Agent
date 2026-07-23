package com.nbcb.assistagent.rag.load;

import org.springframework.ai.document.Document;

import java.util.List;

public record LoadedDocumentSource(
        String source,
        String checksum,
        List<Document> documents) {

    public LoadedDocumentSource {
        documents = List.copyOf(documents);
    }
}
