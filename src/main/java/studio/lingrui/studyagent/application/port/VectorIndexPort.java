package studio.lingrui.studyagent.application.port;

import studio.lingrui.studyagent.domain.rag.DocumentSourceType;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;

import java.util.List;

/**
 * 向量索引端口：RAG 文档的写入与检索。
 *
 * <p>说明：当前进程内实现（SimpleVectorStore）为共享存储，检索在适配器内按 userId 过滤，
 * 保证"只能检索到自己的知识库"；换成支持元数据过滤的向量库（pgvector 等）时可下推为服务端过滤。
 */
public interface VectorIndexPort {

    /**
     * 索引一份文档（内部切分并向量化文本片段），返回分块数量。
     *
     * @param deleteExistingCount 该文档先前已索引的块数（用于覆盖式删除旧向量），0 表示无旧块
     * @param userId              文档归属用户（写入向量元数据，用于检索隔离）
     */
    int indexDocument(Long docId, String docName, DocumentSourceType sourceType,
                      String fullText, int chunkSize, int chunkOverlap,
                      int deleteExistingCount, Long userId);

    /**
     * 删除文档在向量库中的记录（按 docId#0..count-1 的块 id）。
     */
    void deleteDocument(Long docId, int chunkCount);

    /**
     * 语义检索（仅返回 userId 归属的文档片段）。
     *
     * @param userId 为 null 时不限用户（内部/管理用途）
     * @return 命中的文档片段（按相关性降序）
     */
    List<RetrievedChunk> search(String query, int topK, double minScore, Long userId);
}
