package studio.lingrui.studyagent.application.port;

import studio.lingrui.studyagent.domain.rag.DocumentSourceType;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;

import java.util.List;

/**
 * 向量索引端口：RAG 文档的写入与检索。
 */
public interface VectorIndexPort {

    /**
     * 索引一份文档（内部切分并向量化文本片段），返回分块数量。
     *
     * @param deleteExistingCount 该文档先前已索引的块数（用于覆盖式删除旧向量），0 表示无旧块
     */
    int indexDocument(Long docId, String docName, DocumentSourceType sourceType,
                      String fullText, int chunkSize, int chunkOverlap, int deleteExistingCount);

    /**
     * 删除文档在向量库中的记录（按 docId#0..count-1 的块 id）。
     */
    void deleteDocument(Long docId, int chunkCount);

    /**
     * 语义检索。
     *
     * @return 命中的文档片段（按相关性降序）
     */
    List<RetrievedChunk> search(String query, int topK, double minScore);
}
