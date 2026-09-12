package studio.lingrui.studyagent.domain.chat;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 消息仓储（领域接口）。
 */
public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    /** 某会话消息（升序；用 Pageable 截取窗口） */
    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId, Pageable pageable);

    /**
     * 某会话消息（<b>倒序</b>）：取"最近 N 条"要用它。
     * 用升序 + {@code PageRequest.of(0, N)} 拿到的是最早的 N 条。
     */
    List<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    long countBySessionIdIn(List<Long> sessionIds);
}
