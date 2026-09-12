package studio.lingrui.studyagent.domain.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

/**
 * 会话中的一条消息（实体化历史，供多轮问答与笔记/分析复用）。
 */
@Getter
@Setter
@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session", columnList = "session_id"),
        @Index(name = "idx_chat_message_user", columnList = "user_id")
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    /** 消息正文；必须显式指定 LONGTEXT —— Hibernate 7 + MySQL 下 @Lob 会映射成 tinytext(255B) */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** RAG 引用来源（JSON 数组：[{docId,docName,score,snippet}]），可为空 */
    @Column(name = "references_json", columnDefinition = "TEXT")
    private String referencesJson;

    /** LLM 调用元数据（JSON：{model,promptTokens,completionTokens}），成本与上下文观测用 */
    @Column(name = "usage_json", columnDefinition = "TEXT")
    private String usageJson;

    public static ChatMessage create(Long sessionId, Long userId, MessageRole role, String content) {
        ChatMessage m = new ChatMessage();
        m.setSessionId(sessionId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    public static ChatMessage withReferences(Long sessionId, Long userId, MessageRole role,
                                             String content, String referencesJson) {
        ChatMessage m = create(sessionId, userId, role, content);
        m.setReferencesJson(referencesJson);
        return m;
    }
}
