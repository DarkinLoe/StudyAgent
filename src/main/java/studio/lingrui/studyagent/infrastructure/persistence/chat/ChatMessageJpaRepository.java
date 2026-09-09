package studio.lingrui.studyagent.infrastructure.persistence.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.chat.ChatMessage;
import studio.lingrui.studyagent.domain.chat.ChatMessageRepository;

@Repository
public interface ChatMessageJpaRepository
        extends JpaRepository<ChatMessage, Long>, ChatMessageRepository {
}
