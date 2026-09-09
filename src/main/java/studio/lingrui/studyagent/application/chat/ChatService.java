package studio.lingrui.studyagent.application.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.chat.ChatMessage;
import studio.lingrui.studyagent.domain.chat.ChatMessageRepository;
import studio.lingrui.studyagent.domain.chat.ChatSession;
import studio.lingrui.studyagent.domain.chat.ChatSessionRepository;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.util.List;

/**
 * 会话管理（创建/列表/改名/归档/消息历史）。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;

    public Page<ChatSession> listSessions(Long userId, int page, int size) {
        return sessions.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public ChatSession getSession(Long userId, Long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND, "会话不存在"));
    }

    public List<ChatMessage> history(Long userId, Long sessionId) {
        getSession(userId, sessionId);
        return messages.findBySessionIdOrderByIdAsc(sessionId, PageRequest.of(0, 10_000));
    }

    @Transactional
    public ChatSession rename(Long userId, Long sessionId, String title) {
        ChatSession session = getSession(userId, sessionId);
        session.rename(title);
        return sessions.save(session);
    }

    @Transactional
    public void archive(Long userId, Long sessionId) {
        ChatSession session = getSession(userId, sessionId);
        session.archive();
        sessions.save(session);
    }
}
