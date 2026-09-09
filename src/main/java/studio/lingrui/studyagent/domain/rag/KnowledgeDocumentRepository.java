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

    void delete(KnowledgeDocument doc);
}
