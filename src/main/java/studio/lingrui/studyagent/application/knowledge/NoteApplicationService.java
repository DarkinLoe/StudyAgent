package studio.lingrui.studyagent.application.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.application.agent.AIPrompts;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.domain.chat.ChatMessage;
import studio.lingrui.studyagent.domain.chat.ChatMessageRepository;
import studio.lingrui.studyagent.domain.chat.ChatSession;
import studio.lingrui.studyagent.domain.chat.ChatSessionRepository;
import studio.lingrui.studyagent.domain.knowledge.NoteSourceType;
import studio.lingrui.studyagent.domain.knowledge.StudyNote;
import studio.lingrui.studyagent.domain.knowledge.StudyNoteRepository;
import studio.lingrui.studyagent.domain.rag.IndexStatus;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.util.List;

/**
 * 学习笔记/整理（应用服务）：手动笔记 CRUD + Agent 自动生成笔记（会话/文档）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteApplicationService {

    private static final int SESSION_MSG_CAP = 50;
    private static final int SESSION_CHAR_CAP = 14_000;
    private static final int DOC_CHAR_CAP = 8_000;

    private final StudyNoteRepository notes;
    private final AiChatPort aiChat;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final KnowledgeDocumentRepository documents;

    @Transactional
    public StudyNote createManual(Long userId, String title, String content, String tagsJson) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题与内容不能为空");
        }
        return notes.save(StudyNote.create(userId, title, content,
                NoteSourceType.MANUAL, null, tagsJson));
    }

    public Page<StudyNote> list(Long userId, int page, int size) {
        return notes.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public StudyNote detail(Long userId, Long noteId) {
        return notes.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOTE_NOT_FOUND, "笔记不存在"));
    }

    @Transactional
    public StudyNote update(Long userId, Long noteId, String title, String content, String tagsJson) {
        StudyNote note = detail(userId, noteId);
        note.edit(title, content, tagsJson);
        return notes.save(note);
    }

    @Transactional
    public void delete(Long userId, Long noteId) {
        StudyNote note = detail(userId, noteId);
        notes.delete(note);
    }

    /**
     * 把一次问答会话自动整理成笔记。
     */
    @Transactional
    public StudyNote autoFromSession(Long userId, Long sessionId, String titleOverride) {
        ChatSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND, "会话不存在"));
        List<ChatMessage> history = messages.findBySessionIdOrderByIdAsc(
                sessionId, PageRequest.of(0, 10_000));
        StringBuilder transcript = new StringBuilder();
        int count = 0;
        for (int i = Math.max(0, history.size() - SESSION_MSG_CAP); i < history.size(); i++) {
            ChatMessage m = history.get(i);
            String who = switch (m.getRole()) {
                case USER -> "学生";
                case ASSISTANT -> "Agent";
                case SYSTEM -> "系统";
            };
            transcript.append(who).append("：").append(m.getContent()).append("\n\n");
            count++;
        }
        if (count == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "会话中没有可整理的消息");
        }
        String excerpt = clip(transcript.toString(), SESSION_CHAR_CAP);
        String content = callAi(AIPrompts.noteFromConversation(excerpt));
        String title = (titleOverride == null || titleOverride.isBlank())
                ? session.getTitle() + " · 学习整理" : titleOverride;
        return notes.save(StudyNote.create(userId, title, content,
                NoteSourceType.AUTO, "session:" + sessionId, null));
    }

    /**
     * 把知识库文档自动整理成笔记（需要文档已索引）。
     */
    @Transactional
    public StudyNote autoFromDocument(Long userId, Long docId, String titleOverride) {
        KnowledgeDocument doc = documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
        if (doc.getStatus() != IndexStatus.INDEXED || doc.getTextContent() == null || doc.getTextContent().isBlank()) {
            throw new BizException(ErrorCode.DOC_NOT_INDEXED, "文档尚未完成索引，暂不能生成整理");
        }
        String excerpt = clip(doc.getTextContent(), DOC_CHAR_CAP);
        String content = callAi(AIPrompts.noteFromDocument(doc.getName(), excerpt));
        String title = (titleOverride == null || titleOverride.isBlank())
                ? doc.getName() + " · 学习整理" : titleOverride;
        return notes.save(StudyNote.create(userId, title, content,
                NoteSourceType.AUTO, "doc:" + docId, doc.getTagsJson()));
    }

    private String callAi(String userPrompt) {
        try {
            return aiChat.ask(
                    "你是一名严谨的学习整理助手，只做整理输出，不闲聊。", userPrompt);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 生成笔记失败", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE,
                    "AI 生成失败，请检查 AI_BASE_URL / AI_API_KEY 配置（" + e.getMessage() + "）");
        }
    }

    private String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n……(内容过长已截断)";
    }
}
