package studio.lingrui.studyagent.infrastructure.persistence.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;

@Repository
public interface KnowledgeDocumentJpaRepository
        extends JpaRepository<KnowledgeDocument, Long>, KnowledgeDocumentRepository {
}
