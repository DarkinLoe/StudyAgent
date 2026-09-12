package studio.lingrui.studyagent.domain.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 个人 RAG 知识库文档（聚合根）：课程表/学习安排/PPT/题目资料等。
 * textContent 保存解析出的纯文本，供重建向量索引与后续提问上下文使用。
 */
@Getter
@Setter
@Entity
@Table(name = "knowledge_document", indexes = {
        @Index(name = "idx_kdoc_user", columnList = "user_id"),
        @Index(name = "idx_kdoc_status", columnList = "status")
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class KnowledgeDocument extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 原始文件名 */
    @Column(nullable = false, length = 300)
    private String name;

    /** 解析后的存储文件名（本地路径） */
    @Column(name = "stored_path", length = 500)
    private String storedPath;

    /** MIME 类型 */
    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private DocumentSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IndexStatus status = IndexStatus.PENDING;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    /** 解析出的纯文本（课程表/PPT/文档内容），用于检索与重建索引；显式 LONGTEXT 以容纳长文档 */
    @Column(name = "text_content", columnDefinition = "LONGTEXT")
    private String textContent;

    /** 该文档切分出的块数 */
    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount = 0;

    /** 标签（JSON 字符串数组） */
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    public static KnowledgeDocument pending(Long userId, String name, String storedPath,
                                            String contentType, long sizeBytes,
                                            DocumentSourceType sourceType, String tagsJson) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setName(name);
        doc.setStoredPath(storedPath);
        doc.setContentType(contentType);
        doc.setSizeBytes(sizeBytes);
        doc.setSourceType(sourceType);
        doc.setStatus(IndexStatus.PENDING);
        doc.setTagsJson(tagsJson);
        return doc;
    }

    public void markIndexed(String textContent, int chunkCount) {
        this.textContent = textContent;
        this.chunkCount = chunkCount;
        this.status = IndexStatus.INDEXED;
        this.errorMsg = null;
        this.indexedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = IndexStatus.FAILED;
        this.errorMsg = error;
    }

    public void markProcessing() {
        this.status = IndexStatus.PROCESSING;
    }
}
