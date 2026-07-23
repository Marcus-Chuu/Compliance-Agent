package com.nbcb.assistagent.rag.index;

import com.nbcb.assistagent.rag.load.AssistDocumentLoader;
import com.nbcb.assistagent.rag.load.LoadedDocumentSource;
import com.nbcb.assistagent.rag.split.RagChunkingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistDocumentIndexServiceTests {

    @Test
    void skipsUnchangedSourceWithoutCallingEmbeddingVectorStore() throws Exception {
        AssistDocumentLoader loader = mock(AssistDocumentLoader.class);
        VectorStore vectorStore = mock(VectorStore.class);
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        RagIndexingProperties indexingProperties = new RagIndexingProperties();
        RagChunkingProperties chunkingProperties = new RagChunkingProperties();
        AssistDocumentIndexService service = new AssistDocumentIndexService(
                loader,
                vectorStore,
                jdbcTemplate,
                indexingProperties,
                chunkingProperties);

        Document document = Document.builder()
                .id("0d7623a8-b60b-3c67-9564-d706e8c3a431")
                .text("测试条款")
                .build();
        LoadedDocumentSource source = new LoadedDocumentSource(
                "test.md",
                "same-checksum",
                List.of(document));
        when(loader.loadMarkdowns()).thenReturn(List.of(source));
        jdbcTemplate.manifests = List.of(new AssistDocumentIndexService.ManifestEntry(
                "test.md",
                "same-checksum",
                service.indexSignature()));

        AssistDocumentIndexService.IndexingSummary summary = service.syncDocuments();

        assertThat(summary.discoveredFiles()).isEqualTo(1);
        assertThat(summary.indexedFiles()).isZero();
        assertThat(summary.skippedFiles()).isEqualTo(1);
        assertThat(summary.indexedChunks()).isZero();
        assertThat(jdbcTemplate.updates).isEmpty();
        verify(vectorStore, never()).add(List.of(document));
    }

    @Test
    void rebuildsSourceWhenChecksumChanges() throws Exception {
        AssistDocumentLoader loader = mock(AssistDocumentLoader.class);
        VectorStore vectorStore = mock(VectorStore.class);
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        RagIndexingProperties indexingProperties = new RagIndexingProperties();
        RagChunkingProperties chunkingProperties = new RagChunkingProperties();
        AssistDocumentIndexService service = new AssistDocumentIndexService(
                loader,
                vectorStore,
                jdbcTemplate,
                indexingProperties,
                chunkingProperties);

        Document document = Document.builder()
                .id("0d7623a8-b60b-3c67-9564-d706e8c3a431")
                .text("更新后的测试条款")
                .build();
        LoadedDocumentSource source = new LoadedDocumentSource(
                "test.md",
                "new-checksum",
                List.of(document));
        when(loader.loadMarkdowns()).thenReturn(List.of(source));
        jdbcTemplate.manifests = List.of(new AssistDocumentIndexService.ManifestEntry(
                "test.md",
                "old-checksum",
                service.indexSignature()));

        AssistDocumentIndexService.IndexingSummary summary = service.syncDocuments();

        assertThat(summary.indexedFiles()).isEqualTo(1);
        assertThat(summary.skippedFiles()).isZero();
        assertThat(summary.indexedChunks()).isEqualTo(1);
        assertThat(jdbcTemplate.updates).hasSize(2);
        verify(vectorStore).add(List.of(document));
    }

    @Test
    void limitsEachDashScopeEmbeddingBatchToTenDocuments() throws Exception {
        AssistDocumentLoader loader = mock(AssistDocumentLoader.class);
        VectorStore vectorStore = mock(VectorStore.class);
        FakeJdbcTemplate jdbcTemplate = new FakeJdbcTemplate();
        RagIndexingProperties indexingProperties = new RagIndexingProperties();
        RagChunkingProperties chunkingProperties = new RagChunkingProperties();
        AssistDocumentIndexService service = new AssistDocumentIndexService(
                loader,
                vectorStore,
                jdbcTemplate,
                indexingProperties,
                chunkingProperties);

        List<Document> documents = IntStream.range(0, 23)
                .mapToObj(index -> Document.builder()
                        .id(String.format("00000000-0000-0000-0000-%012d", index))
                        .text("测试条款 " + index)
                        .build())
                .toList();
        when(loader.loadMarkdowns()).thenReturn(List.of(new LoadedDocumentSource(
                "test.md",
                "checksum",
                documents)));

        AssistDocumentIndexService.IndexingSummary summary = service.syncDocuments();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(batchCaptor.capture());
        assertThat(batchCaptor.getAllValues()).extracting(List::size).containsExactly(10, 10, 3);
        assertThat(summary.indexedChunks()).isEqualTo(23);
    }

    private static final class FakeJdbcTemplate extends JdbcTemplate {

        private List<AssistDocumentIndexService.ManifestEntry> manifests = List.of();

        private final List<String> updates = new ArrayList<>();

        @Override
        public void execute(String sql) {
            // Manifest DDL is irrelevant to this unit test.
        }

        @Override
        public <T> T execute(ConnectionCallback<T> action) {
            // Advisory locking is covered by PostgreSQL itself; no connection is needed here.
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) manifests;
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql);
            return 1;
        }
    }
}
