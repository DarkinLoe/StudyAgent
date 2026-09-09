package studio.lingrui.studyagent.infrastructure.persistence.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.chat.ChatSession;
import studio.lingrui.studyagent.domain.chat.ChatSessionRepository;

@Repository
public interface ChatSessionJpaRepository
        extends JpaRepository<ChatSession, Long>, ChatSessionRepository {
}
