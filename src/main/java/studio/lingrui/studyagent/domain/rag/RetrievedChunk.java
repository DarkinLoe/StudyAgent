package studio.lingrui.studyagent.domain.rag;

/**
 * 检索命中块（RAG 输出给提示词拼装的素材）。
 */
public record RetrievedChunk(
        Long docId,
        String docName,
        DocumentSourceType sourceType,
        double score,
        String text
) {
}
