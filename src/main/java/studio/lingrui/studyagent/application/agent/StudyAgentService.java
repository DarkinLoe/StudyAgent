package studio.lingrui.studyagent.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.application.agent.skill.AgentSkill;
import studio.lingrui.studyagent.application.agent.skill.AgentSkillFactory;
import studio.lingrui.studyagent.application.chat.ChatReply;
import studio.lingrui.studyagent.application.chat.ChatReply.Reference;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.application.port.AiChatResult;
import studio.lingrui.studyagent.application.port.ChatTurn;
import studio.lingrui.studyagent.application.port.TurnRole;
import studio.lingrui.studyagent.application.port.VectorIndexPort;
import studio.lingrui.studyagent.domain.chat.ChatMessage;
import studio.lingrui.studyagent.domain.chat.ChatMessageRepository;
import studio.lingrui.studyagent.domain.chat.ChatSession;
import studio.lingrui.studyagent.domain.chat.ChatSessionRepository;
import studio.lingrui.studyagent.domain.chat.MessageRole;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单 Agent 问答服务（编排层）：
 * 会话历史（MySQL）→ RAG 检索（向量库）→ 组装系统提示 → LLM 调用 → 落库会话与引用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyAgentService {

    private static final int HISTORY_SIZE = 10;
    private static final int TITLE_LENGTH = 20;

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final VectorIndexPort vectorIndex;
    private final AiChatPort aiChat;
    private final AgentSkillFactory agentSkillFactory;
    private final StudyAgentProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 发送一条用户消息并返回 Agent 回答。
     *
     * @param sessionId 可空：为空时自动新建会话
     */
    @Transactional
    public ChatReply send(Long userId, Long sessionId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
        }

        ChatSession session;
        if (sessionId == null) {
            session = sessions.save(ChatSession.create(userId, defaultTitle(text)));
        } else {
            session = sessions.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND, "会话不存在"));
        }

        // 历史（当前消息之前）
        List<ChatMessage> history = messages.findBySessionIdOrderByIdAsc(
                session.getId(), PageRequest.of(0, HISTORY_SIZE));

        messages.save(ChatMessage.create(session.getId(), userId, MessageRole.USER, text));

        List<RetrievedChunk> hits = retrieve(text);
        // 按当前用户构建可用技能（工具），并在系统提示中说明使用规则
        List<AgentSkill> skills = agentSkillFactory.forUser(userId);
        String systemPrompt = AIPrompts.tutorSystem(hits)
                + (skills.isEmpty() ? "" : AIPrompts.toolGuide(skills));

        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn(TurnRole.SYSTEM, systemPrompt));
        for (ChatMessage m : history) {
            TurnRole role = switch (m.getRole()) {
                case USER -> TurnRole.USER;
                case ASSISTANT -> TurnRole.ASSISTANT;
                case SYSTEM -> null;
            };
            if (role != null) {
                turns.add(new ChatTurn(role, m.getContent()));
            }
        }
        turns.add(new ChatTurn(TurnRole.USER, text));

        AiChatResult result = callModel(turns, skills);
        String answer = result.text();
        String usageJson = toUsageJson(result);
        String referencesJson = toReferencesJson(hits);
        ChatMessage assistantMessage = ChatMessage.withReferences(
                session.getId(), userId, MessageRole.ASSISTANT, answer, referencesJson);
        assistantMessage.setUsageJson(usageJson);
        ChatMessage saved = messages.save(assistantMessage);
        session.touch();
        sessions.save(session);

        return new ChatReply(session.getId(), saved.getId(), answer, toReferences(hits));
    }

    private List<RetrievedChunk> retrieve(String query) {
        try {
            return vectorIndex.search(query, props.getRag().getTopK(), props.getRag().getMinScore());
        } catch (Exception e) {
            log.warn("RAG 检索失败，将无上下文回答: {}", e.getMessage());
            return List.of();
        }
    }

    private AiChatResult callModel(List<ChatTurn> turns, List<AgentSkill> skills) {
        try {
            return aiChat.chat(turns, skills);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 模型调用失败", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务调用失败，请检查 AI_BASE_URL / AI_API_KEY / AI_CHAT_MODEL 配置（" + e.getMessage() + "）");
        }
    }

    private String toUsageJson(AiChatResult result) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("model", result.model());
            m.put("promptTokens", result.promptTokens());
            m.put("completionTokens", result.completionTokens());
            m.put("totalTokens", result.totalTokens());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("用量序列化失败", e);
            return null;
        }
    }

    private List<Reference> toReferences(List<RetrievedChunk> hits) {
        return hits.stream()
                .map(h -> new Reference(h.docId(), h.docName(),
                        h.sourceType() == null ? null : h.sourceType().name(),
                        h.score(), snippet(h.text())))
                .toList();
    }

    private String toReferencesJson(List<RetrievedChunk> hits) {
        try {
            List<Map<String, Object>> list = hits.stream()
                    .map(h -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("docId", h.docId());
                        m.put("docName", h.docName());
                        m.put("sourceType", h.sourceType() == null ? null : h.sourceType().name());
                        m.put("score", h.score());
                        m.put("snippet", snippet(h.text()));
                        return m;
                    })
                    .toList();
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("引用序列化失败", e);
            return "[]";
        }
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 160 ? text : text.substring(0, 160) + "…";
    }

    private String defaultTitle(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= TITLE_LENGTH ? oneLine : oneLine.substring(0, TITLE_LENGTH) + "…";
    }
}
