package com.nbcb.assistagent.rag.load;

import com.nbcb.assistagent.rag.index.RagIndexingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AssistDocumentLoaderTests {

    @Test
    void loadsResourcesByArticleAndKeepsStableIdsAndMetadata() throws Exception {
        AssistDocumentLoader loader = createLoader(500, 100, 20);

        List<LoadedDocumentSource> firstLoad = loader.loadMarkdowns();
        List<LoadedDocumentSource> secondLoad = loader.loadMarkdowns();

        assertThat(firstLoad).hasSize(5);
        assertThat(firstLoad.stream().mapToInt(source -> source.documents().size()).sum())
                .isEqualTo(83);
        assertThat(firstLoad.stream().map(LoadedDocumentSource::checksum))
                .allSatisfy(checksum -> assertThat(checksum).hasSize(64));

        List<Document> firstDocuments = firstLoad.stream()
                .flatMap(source -> source.documents().stream())
                .toList();
        List<Document> secondDocuments = secondLoad.stream()
                .flatMap(source -> source.documents().stream())
                .toList();

        assertThat(secondDocuments.stream().map(Document::getId).toList())
                .containsExactlyElementsOf(firstDocuments.stream().map(Document::getId).toList());
        assertThat(new HashSet<>(firstDocuments.stream().map(Document::getId).toList()))
                .hasSameSizeAs(firstDocuments);

        Document riskArticle = firstDocuments.stream()
                .filter(document -> "CREDIT-RISK-004".equals(document.getMetadata().get("doc_id")))
                .filter(document -> "第五条".equals(document.getMetadata().get("articleNumber")))
                .findFirst()
                .orElseThrow();

        assertThat(riskArticle.getText())
                .startsWith("文档：贷款风险分类认定标准\n章节：第三章 不良贷款认定\n条款：第五条 次级类标准")
                .contains("本金或利息逾期 31 至 90 天")
                .doesNotContain("doc_id:");
        assertThat(riskArticle.getMetadata())
                .containsEntry("version", "V2.0")
                .containsEntry("status", "现行")
                .containsEntry("source", "04_贷款风险分类认定标准.md")
                .containsEntry("collection", "assist-documents");
    }

    @Test
    void splitsOnlyOversizedArticleAndRepeatsItsContext() {
        AssistDocumentLoader loader = createLoader(20, 10, 5);
        String markdown = """
                ---
                doc_id: TEST-001
                title: 测试制度
                version: V1.0
                ---

                # 测试制度
                ## 第一章 测试章节
                ### 第一条 测试条款
                这是第一段较长的测试内容，用来验证超长条款能够继续切分。这里继续补充一些文字，确保内容超过配置的Token数量。

                这是第二段测试内容，用来验证每一个子块都会重复携带文档、章节和条款上下文，避免检索结果丢失规则归属。
                """;

        List<Document> documents = loader.parseMarkdown("test.md", markdown);

        assertThat(documents).hasSizeGreaterThan(1);
        assertThat(documents)
                .allSatisfy(document -> {
                    assertThat(document.getText()).startsWith(
                            "文档：测试制度\n章节：第一章 测试章节\n条款：第一条 测试条款\n\n");
                    assertThat(document.getMetadata()).containsEntry("doc_id", "TEST-001");
                });
        assertThat(documents.stream().map(document -> document.getMetadata().get("chunkIndex")))
                .containsExactlyElementsOf(IntStream.range(0, documents.size()).boxed().toList());
    }

    private AssistDocumentLoader createLoader(
            int chunkSize,
            int minChunkSizeChars,
            int minChunkLengthToEmbed) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(minChunkLengthToEmbed)
                .withMaxNumChunks(100)
                .withKeepSeparator(true)
                .withPunctuationMarks(List.of('。', '；', '？', '！', '\n'))
                .build();
        RagIndexingProperties indexingProperties = new RagIndexingProperties();
        return new AssistDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                splitter,
                indexingProperties);
    }
}
