package studio.lingrui.studyagent.domain.chat;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 消息仓储（领域接口）。
 */
public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    /** 某会话消息（升序；用 Pageable 截取最近 N 条或取全部） */
    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId, Pageable pageable);

    long countBySessionIdIn(List<Long> sessionIds);
}
