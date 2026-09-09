package studio.lingrui.studyagent.infrastructure.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.application.port.VectorIndexPort;
import studio.lingrui.studyagent.domain.rag.DocumentSourceType;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 向量索引适配器：
 * - 写入：文档全文 → 分块 → EmbeddingModel 向量化 → SimpleVectorStore（进程内，重启由摄入流程重建）
 * - 检索：query → embedding → 相似度 Top-K
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiVectorIndexAdapter implements VectorIndexPort {

    private final EmbeddingModel embeddingModel;

    private volatile VectorStore store;

    private VectorStore store() {
        VectorStore s = this.store;
        if (s == null) {
            synchronized (this) {
                if (this.store == null) {
                    this.store = SimpleVectorStore.builder(embeddingModel).build();
                }
                s = this.store;
            }
        }
        return s;
    }

    @Override
    public int indexDocument(Long docId, String docName, DocumentSourceType sourceType,
                             String fullText, int chunkSize, int chunkOverlap, int deleteExistingCount) {
        deleteDocument(docId, deleteExistingCount); // 覆盖式重建
        List<String> chunks = TextChunker.split(fullText, chunkSize, chunkOverlap);
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("docId", docId);
            meta.put("docName", docName);
            meta.put("sourceType", sourceType.name());
            docs.add(new Document(docId + "#" + i, chunks.get(i), meta));
        }
        if (!docs.isEmpty()) {
            store().add(docs);
        }
        log.info("已索引文档 docId={} chunks={}", docId, docs.size());
        return docs.size();
    }

    @Override
    public void deleteDocument(Long docId, int chunkCount) {
        List<String> ids = new ArrayList<>();
        int n = Math.min(Math.max(chunkCount, 0), 10_000);
        for (int i = 0; i < n; i++) {
            ids.add(docId + "#" + i);
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            store().delete(ids);
        } catch (Exception e) {
            log.warn("删除向量块 docId={} 失败(忽略): {}", docId, e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK, double minScore) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK, 1))
                .similarityThreshold((float) minScore)
                .build();
        List<Document> hits = store().similaritySearch(request);
        return hits.stream()
                .map(d -> new RetrievedChunk(
                        docIdOf(d.getMetadata()),
                        nameOf(d.getMetadata()),
                        sourceOf(d.getMetadata()),
                        d.getScore() == null ? 0 : d.getScore(),
                        d.getText()))
                .toList();
    }

    private Long docIdOf(Map<String, Object> meta) {
        Object v = meta.get("docId");
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? null : Long.valueOf(v.toString());
    }

    private String nameOf(Map<String, Object> meta) {
        Object v = meta.get("docName");
        return v == null ? "unknown" : v.toString();
    }

    private DocumentSourceType sourceOf(Map<String, Object> meta) {
        Object v = meta.get("sourceType");
        try {
            return v == null ? DocumentSourceType.OTHER : DocumentSourceType.valueOf(v.toString());
        } catch (IllegalArgumentException e) {
            return DocumentSourceType.OTHER;
        }
    }
}
