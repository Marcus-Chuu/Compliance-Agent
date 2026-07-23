package com.nbcb.assistagent.rag.load;

import com.nbcb.assistagent.rag.index.RagIndexingProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AssistDocumentLoader {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile("^(第\\S+条)\\s*(.*)$");

    private final ResourcePatternResolver resourcePatternResolver;

    private final TokenTextSplitter tokenTextSplitter;

    private final RagIndexingProperties indexingProperties;

    public AssistDocumentLoader(
            ResourcePatternResolver resourcePatternResolver,
            TokenTextSplitter tokenTextSplitter,
            RagIndexingProperties indexingProperties) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.tokenTextSplitter = tokenTextSplitter;
        this.indexingProperties = indexingProperties;
    }

    /**
     * 按文件加载 Markdown。每个三级标题（条款）形成一个基础块，只有超长条款
     * 才会继续交给 TokenTextSplitter 拆分。
     */
    public List<LoadedDocumentSource> loadMarkdowns() throws IOException {
        Resource[] resources = resourcePatternResolver.getResources(indexingProperties.getResourcePattern());
        Arrays.sort(resources, Comparator.comparing(this::sourceName));

        List<LoadedDocumentSource> sources = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            byte[] bytes = resource.getContentAsByteArray();
            String source = sourceName(resource);
            String checksum = sha256(bytes);
            List<Document> documents = parseMarkdown(source, new String(bytes, StandardCharsets.UTF_8));
            sources.add(new LoadedDocumentSource(source, checksum, documents));
        }
        return List.copyOf(sources);
    }

    List<Document> parseMarkdown(String source, String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        FrontMatter frontMatter = readFrontMatter(lines);
        Map<String, Object> sourceMetadata = new LinkedHashMap<>(frontMatter.metadata());
        sourceMetadata.put("source", source);
        sourceMetadata.put("fileName", source);
        sourceMetadata.put("collection", indexingProperties.getCollection());

        String documentTitle = stringValue(sourceMetadata.get("title"), stripExtension(source));
        String chapter = "";
        String articleHeading = null;
        List<String> articleBody = new ArrayList<>();
        List<Article> articles = new ArrayList<>();

        for (int i = frontMatter.bodyStart(); i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("### ")) {
                flushArticle(articles, documentTitle, chapter, articleHeading, articleBody);
                articleHeading = line.substring(4).trim();
                articleBody.clear();
            }
            else if (line.startsWith("## ")) {
                flushArticle(articles, documentTitle, chapter, articleHeading, articleBody);
                articleHeading = null;
                articleBody.clear();
                chapter = line.substring(3).trim();
            }
            else if (line.startsWith("# ")) {
                flushArticle(articles, documentTitle, chapter, articleHeading, articleBody);
                articleHeading = null;
                articleBody.clear();
                documentTitle = line.substring(2).trim();
            }
            else if (articleHeading != null) {
                articleBody.add(line);
            }
        }
        flushArticle(articles, documentTitle, chapter, articleHeading, articleBody);

        List<Document> documents = new ArrayList<>();
        for (Article article : articles) {
            documents.addAll(toDocuments(source, sourceMetadata, article));
        }
        return List.copyOf(documents);
    }

    private List<Document> toDocuments(
            String source,
            Map<String, Object> sourceMetadata,
            Article article) {
        Document bodyDocument = Document.builder().text(article.body()).build();
        List<Document> splitBodies = tokenTextSplitter.apply(List.of(bodyDocument));
        if (splitBodies.isEmpty()) {
            splitBodies = List.of(bodyDocument);
        }

        Matcher matcher = ARTICLE_PATTERN.matcher(article.heading());
        String articleNumber = matcher.matches() ? matcher.group(1) : article.heading();
        String articleTitle = matcher.matches() ? matcher.group(2) : "";
        int chunkCount = splitBodies.size();
        List<Document> documents = new ArrayList<>(chunkCount);

        for (int index = 0; index < chunkCount; index++) {
            Map<String, Object> metadata = new LinkedHashMap<>(sourceMetadata);
            metadata.put("documentTitle", article.documentTitle());
            metadata.put("chapter", article.chapter());
            metadata.put("articleNumber", articleNumber);
            metadata.put("articleTitle", articleTitle);
            metadata.put("chunkIndex", index);
            metadata.put("chunkCount", chunkCount);

            String stableKey = source + "\u0000" + article.heading() + "\u0000" + index;
            String id = UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8)).toString();
            String text = contextualText(article, splitBodies.get(index).getText());
            documents.add(Document.builder().id(id).text(text).metadata(metadata).build());
        }
        return documents;
    }

    private String contextualText(Article article, String body) {
        StringBuilder text = new StringBuilder()
                .append("文档：").append(article.documentTitle()).append('\n');
        if (!article.chapter().isBlank()) {
            text.append("章节：").append(article.chapter()).append('\n');
        }
        text.append("条款：").append(article.heading()).append("\n\n");
        return text.append(body.strip()).toString();
    }

    private void flushArticle(
            List<Article> articles,
            String documentTitle,
            String chapter,
            String articleHeading,
            List<String> articleBody) {
        if (articleHeading == null) {
            return;
        }
        String body = String.join("\n", articleBody).strip();
        if (!body.isBlank()) {
            articles.add(new Article(documentTitle, chapter, articleHeading, body));
        }
    }

    private FrontMatter readFrontMatter(String[] lines) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (lines.length == 0 || !lines[0].strip().equals("---")) {
            return new FrontMatter(metadata, 0);
        }

        int index = 1;
        while (index < lines.length && !lines[index].strip().equals("---")) {
            String line = lines[index].strip();
            int delimiter = line.indexOf(':');
            if (delimiter > 0) {
                String key = line.substring(0, delimiter).strip();
                String value = unquote(line.substring(delimiter + 1).strip());
                metadata.put(key, value);
            }
            index++;
        }
        return new FrontMatter(metadata, Math.min(index + 1, lines.length));
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String sourceName(Resource resource) {
        String filename = resource.getFilename();
        return filename != null ? filename : resource.getDescription();
    }

    private String stripExtension(String source) {
        int dot = source.lastIndexOf('.');
        return dot > 0 ? source.substring(0, dot) : source;
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record FrontMatter(Map<String, Object> metadata, int bodyStart) {
    }

    private record Article(String documentTitle, String chapter, String heading, String body) {
    }
}
