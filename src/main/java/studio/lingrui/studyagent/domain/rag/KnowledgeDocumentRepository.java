package studio.lingrui.studyagent.domain.rag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 知识文档仓储（领域接口）。
 */
public interface KnowledgeDocumentRepository {

    KnowledgeDocument save(KnowledgeDocument doc);

    Optional<KnowledgeDocument> findById(Long id);

    Optional<KnowledgeDocument> findByIdAndUserId(Long id, Long userId);

    Page<KnowledgeDocument> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 当前用户所有已索引文档（重建向量库用） */
    List<KnowledgeDocument> findByUserIdAndStatus(Long userId, IndexStatus status);

    /**
     * 当前用户已索引文档（按 id 倒序分页）。
     * 关键词降级检索需要它来控制扫描量：不建议把某个用户的全部 longtext 正文一次性读进内存。
     */
    List<KnowledgeDocument> findByUserIdAndStatusOrderByIdDesc(Long userId, IndexStatus status, Pageable pageable);

    /** 全部指定状态的文档（启动重建向量索引用） */
    List<KnowledgeDocument> findByStatus(IndexStatus status);

    void delete(KnowledgeDocument doc);
}
