package studio.lingrui.studyagent.domain.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 问答教学会话（聚合根）。
 */
@Getter
@Setter
@Entity
@Table(name = "chat_session")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ChatSession extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    /** 会话标题，未命名时为第一条用户消息摘要 */
    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatSessionStatus status = ChatSessionStatus.ACTIVE;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public static ChatSession create(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setStatus(ChatSessionStatus.ACTIVE);
        session.setLastMessageAt(LocalDateTime.now());
        return session;
    }

    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }

    public void rename(String newTitle) {
        this.title = newTitle;
    }

    public void archive() {
        this.status = ChatSessionStatus.ARCHIVED;
    }
}
