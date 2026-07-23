package com.nbcb.assistagent.rag.index;

import com.nbcb.assistagent.rag.load.AssistDocumentLoader;
import com.nbcb.assistagent.rag.load.LoadedDocumentSource;
import com.nbcb.assistagent.rag.pgdata.PgVectorStoreConfig;
import com.nbcb.assistagent.rag.split.RagChunkingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class AssistDocumentIndexService {

    private static final long INDEX_LOCK_KEY = 4_702_979_855_826_464_321L;

    private static final String MANIFEST_TABLE = "public.rag_source_manifest";

    private final AssistDocumentLoader documentLoader;

    private final VectorStore vectorStore;

    private final JdbcTemplate jdbcTemplate;

    private final RagIndexingProperties indexingProperties;

    private final RagChunkingProperties chunkingProperties;

    public AssistDocumentIndexService(
            AssistDocumentLoader documentLoader,
            @Qualifier("pgVectorStore") VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            RagIndexingProperties indexingProperties,
            RagChunkingProperties chunkingProperties) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.indexingProperties = indexingProperties;
        this.chunkingProperties = chunkingProperties;
    }

    /**
     * 将 classpath 文档与向量库同步。整个过程处于同一事务，并使用 PostgreSQL
     * 事务级 advisory lock，避免多个应用实例同时重建同一份索引。
     */
    @Transactional
    public IndexingSummary syncDocuments() throws Exception {
        acquireIndexLock();

        List<LoadedDocumentSource> loadedSources = documentLoader.loadMarkdowns();
        String collection = indexingProperties.getCollection();
        String indexSignature = indexSignature();
        Map<String, ManifestEntry> manifests = loadManifests(collection);
        Set<String> currentSources = new HashSet<>();

        int indexedFiles = 0;
        int skippedFiles = 0;
        int removedFiles = 0;
        int indexedChunks = 0;

        for (LoadedDocumentSource source : loadedSources) {
            currentSources.add(source.source());
            ManifestEntry manifest = manifests.get(source.source());
            if (manifest != null
                    && manifest.checksum().equals(source.checksum())
                    && manifest.indexSignature().equals(indexSignature)) {
                skippedFiles++;
                continue;
            }

            deleteVectors(collection, source.source());
            if (!source.documents().isEmpty()) {
                addInEmbeddingBatches(source.documents());
            }
            upsertManifest(collection, source, indexSignature);
            indexedFiles++;
            indexedChunks += source.documents().size();
            log.info("Indexed RAG source: source={}, chunks={}", source.source(), source.documents().size());
        }

        for (String knownSource : manifests.keySet()) {
            if (!currentSources.contains(knownSource)) {
                deleteVectors(collection, knownSource);
                jdbcTemplate.update(
                        "DELETE FROM " + MANIFEST_TABLE + " WHERE collection = ? AND source = ?",
                        collection,
                        knownSource);
                removedFiles++;
                log.info("Removed stale RAG source: source={}", knownSource);
            }
        }

        return new IndexingSummary(
                loadedSources.size(), indexedFiles, skippedFiles, removedFiles, indexedChunks);
    }

    private void acquireIndexLock() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, INDEX_LOCK_KEY);
                statement.execute();
            }
            return null;
        });
    }

    private Map<String, ManifestEntry> loadManifests(String collection) {
        List<ManifestEntry> entries = jdbcTemplate.query(
                "SELECT source, checksum, index_signature FROM " + MANIFEST_TABLE
                        + " WHERE collection = ?",
                (resultSet, rowNumber) -> new ManifestEntry(
                        resultSet.getString("source"),
                        resultSet.getString("checksum"),
                        resultSet.getString("index_signature")),
                collection);
        Map<String, ManifestEntry> manifests = new HashMap<>();
        for (ManifestEntry entry : entries) {
            manifests.put(entry.source(), entry);
        }
        return manifests;
    }

    private void deleteVectors(String collection, String source) {
        String vectorTable = PgVectorStoreConfig.VECTOR_SCHEMA + "." + PgVectorStoreConfig.VECTOR_TABLE;
        jdbcTemplate.update(
                "DELETE FROM " + vectorTable
                        + " WHERE metadata ->> 'collection' = ? AND metadata ->> 'source' = ?",
                collection,
                source);
    }

    private void upsertManifest(
            String collection,
            LoadedDocumentSource source,
            String indexSignature) {
        jdbcTemplate.update("""
                        INSERT INTO public.rag_source_manifest
                            (collection, source, checksum, index_signature, chunk_count, indexed_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (collection, source) DO UPDATE SET
                            checksum = EXCLUDED.checksum,
                            index_signature = EXCLUDED.index_signature,
                            chunk_count = EXCLUDED.chunk_count,
                            indexed_at = CURRENT_TIMESTAMP
                        """,
                collection,
                source.source(),
                source.checksum(),
                indexSignature,
                source.documents().size());
    }

    private void addInEmbeddingBatches(List<Document> documents) {
        int batchSize = indexingProperties.getEmbeddingBatchSize();
        if (batchSize < 1 || batchSize > 10) {
            throw new IllegalArgumentException(
                    "assist.rag.indexing.embedding-batch-size must be between 1 and 10 for DashScope");
        }
        for (int fromIndex = 0; fromIndex < documents.size(); fromIndex += batchSize) {
            int toIndex = Math.min(fromIndex + batchSize, documents.size());
            vectorStore.add(List.copyOf(documents.subList(fromIndex, toIndex)));
        }
    }

    String indexSignature() {
        return String.join("|",
                "version=" + indexingProperties.getIndexVersion(),
                "embeddingModel=" + indexingProperties.getEmbeddingModel(),
                "embeddingDimensions=" + indexingProperties.getEmbeddingDimensions(),
                "strategy=" + chunkingProperties.getStrategy(),
                "maxTokens=" + chunkingProperties.getMaxTokens(),
                "minChunkSizeChars=" + chunkingProperties.getMinChunkSizeChars(),
                "minChunkLengthToEmbed=" + chunkingProperties.getMinChunkLengthToEmbed(),
                "maxNumChunks=" + chunkingProperties.getMaxNumChunks(),
                "keepSeparator=" + chunkingProperties.isKeepSeparator());
    }

    record ManifestEntry(String source, String checksum, String indexSignature) {
    }

    public record IndexingSummary(
            int discoveredFiles,
            int indexedFiles,
            int skippedFiles,
            int removedFiles,
            int indexedChunks) {
    }
}
