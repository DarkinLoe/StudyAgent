package studio.lingrui.studyagent.application.chat;

import java.util.List;

/**
 * Agent 单轮回答结果。
 */
public record ChatReply(
        Long sessionId,
        Long messageId,
        String content,
        List<Reference> references
) {

    /**
     * RAG 引用来源。
     */
    public record Reference(Long docId, String docName, String sourceType,
                            double score, String snippet) {
    }
}
