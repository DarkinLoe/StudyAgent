package studio.lingrui.studyagent.domain.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 会话仓储（领域接口，由基础设施层以 Spring Data 实现）。
 */
public interface ChatSessionRepository {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(Long id);

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    Page<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
}
