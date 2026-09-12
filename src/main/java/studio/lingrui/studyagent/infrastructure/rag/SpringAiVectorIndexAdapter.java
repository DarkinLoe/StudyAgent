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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RAG 向量索引适配器：
 * - 写入：文档全文 → 分块 → EmbeddingModel 向量化 → SimpleVectorStore（进程内，重启由启动器重建）
 * - 检索：query → embedding → 相似度 Top-K → 按 userId 过滤（检索隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiVectorIndexAdapter implements VectorIndexPort {

    /**
     * 候选放大倍数/下限/上限。
     *
     * <p>SimpleVectorStore 不支持按元数据做服务端过滤，只能"多取候选 → 应用侧按 userId 过滤"。
     * 取少了会出现"前 N 条恰好全是别人的文档"，被误判成"无命中"→ 错误降级到关键词检索（漏召）。
     * 放大本身很廉价：SimpleVectorStore 本来就要和全量文档算相似度，topK 只影响返回与排序。
     */
    private static final int CANDIDATE_MULTIPLIER = 20;
    private static final int MIN_CANDIDATES = 100;
    private static final int MAX_CANDIDATES = 500;

    /** 单文档分块数上限：防御异常的 chunkCount 造成超大循环 */
    private static final int MAX_CHUNKS_PER_DOC = 10_000;

    private final EmbeddingModel embeddingModel;

    private volatile VectorStore store;

    /**
     * 已索引分块 id 登记表（docId → chunkId 集合）。
     *
     * <p>只用"按 chunkCount 枚举 docId#0..n-1"删除是不够的：上一次索引中途失败时
     * chunkCount 没被更新，那些已写入的块就成了永远删不掉的脏向量，还会被检索命中。
     * 登记表是本进程的权威视图（进程重启后向量库本就为空，由启动重建流程重新登记）。
     */
    private final Map<Long, Set<String>> chunkIdsByDoc = new ConcurrentHashMap<>();

    /**
     * 向量库读写锁：SimpleVectorStore 内部是普通 Map，读（检索）与写（索引/删除）并发会读到不一致状态。
     * 用读写锁让"多读并发、读写互斥"，在保证线程安全的同时不牺牲检索吞吐。
     */
    private final ReadWriteLock storeLock = new ReentrantReadWriteLock();

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
                             String fullText, int chunkSize, int chunkOverlap,
                             int deleteExistingCount, Long userId) {
        deleteDocument(docId, deleteExistingCount); // 覆盖式重建
        List<String> chunks = TextChunker.split(fullText, chunkSize, chunkOverlap);
        if (chunks.isEmpty()) {
            chunkIdsByDoc.remove(docId);
            return 0;
        }
        List<Document> docs = new ArrayList<>(chunks.size());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            String id = docId + "#" + i;
            ids.add(id);
            Map<String, Object> meta = new HashMap<>();
            meta.put("docId", docId);
            meta.put("docName", docName);
            meta.put("sourceType", sourceType.name());
            if (userId != null) {
                meta.put("userId", userId);
            }
            docs.add(new Document(id, chunks.get(i), meta));
        }
        storeLock.writeLock().lock();
        try {
            store().add(docs);
            chunkIdsByDoc.put(docId, ids);
        } finally {
            storeLock.writeLock().unlock();
        }
        log.info("已索引文档 docId={} userId={} chunks={}", docId, userId, docs.size());
        return docs.size();
    }

    @Override
    public void deleteDocument(Long docId, int chunkCount) {
        Set<String> ids = new HashSet<>();
        Set<String> tracked = chunkIdsByDoc.remove(docId);
        if (tracked != null) {
            ids.addAll(tracked);
        }
        // 登记表缺失时（例如本进程刚重启、还没重建索引）退化为按 chunkCount 枚举
        int n = Math.min(Math.max(chunkCount, 0), MAX_CHUNKS_PER_DOC);
        for (int i = 0; i < n; i++) {
            ids.add(docId + "#" + i);
        }
        if (ids.isEmpty() || this.store == null) {
            // 本进程还没有向量库：没有东西可删，也不必为了删除去初始化一个空库
            return;
        }
        try {
            storeLock.writeLock().lock();
            try {
                store().delete(new ArrayList<>(ids));
            } finally {
                storeLock.writeLock().unlock();
            }
        } catch (Exception e) {
            log.warn("删除向量块 docId={} 失败(忽略): {}", docId, e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK, double minScore, Long userId) {
        if (this.store == null) {
            // 还没有任何已索引内容：直接返回空，省掉一次无意义的 embedding 调用
            return List.of();
        }
        int k = Math.max(topK, 1);
        int fetch = Math.min(Math.max(k * CANDIDATE_MULTIPLIER, MIN_CANDIDATES), MAX_CANDIDATES);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(fetch)
                .similarityThreshold((float) minScore)
                .build();
        List<Document> hits;
        storeLock.readLock().lock();
        try {
            hits = store().similaritySearch(request);
        } finally {
            storeLock.readLock().unlock();
        }
        return hits.stream()
                .filter(d -> userId == null || userId.equals(userIdOf(d.getMetadata())))
                .limit(k)
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

    private Long userIdOf(Map<String, Object> meta) {
        Object v = meta.get("userId");
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
