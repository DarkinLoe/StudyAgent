package studio.lingrui.studyagent.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import studio.lingrui.studyagent.application.agent.skill.AgentSkill;
import studio.lingrui.studyagent.application.agent.skill.AgentSkillFactory;
import studio.lingrui.studyagent.application.chat.ChatReply;
import studio.lingrui.studyagent.application.rag.KnowledgeKeywordSearchService;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单 Agent 问答服务（编排层）：
 * 会话历史（MySQL）→ RAG 检索（向量库）→ 组装系统提示 → LLM 调用 → 落库会话与引用。
 *
 * <p><b>事务边界</b>：整轮问答被刻意切成「短事务 → 事务外调模型 → 短事务」三段。
 * 大模型调用是 10~60 秒级慢操作，若整段套 {@code @Transactional}：
 * ① 一条连接会被独占整个模型响应时间，连接池（maximum-pool-size）很快耗尽，
 *    一次对话高峰就能把全站接口拖垮；② 上游超时后用户消息随事务一起回滚，
 *    会话里会凭空少一条。因此这里用 {@link TransactionTemplate} 显式圈出两段写事务。
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
    private final KnowledgeKeywordSearchService keywordSearch;
    private final AiChatPort aiChat;
    private final AgentSkillFactory agentSkillFactory;
    private final StudyAgentProperties props;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    /** 一轮问答的准备结果（会话 id、用户 id、该会话之前的历史） */
    private record TurnContext(Long sessionId, Long userId, List<ChatMessage> history) {
    }

    /**
     * 发送一条用户消息并返回 Agent 回答。
     *
     * @param sessionId 可空：为空时自动新建会话
     */
    public ChatReply send(Long userId, Long sessionId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
        }

        // ① 短事务：确定会话、取历史、落用户消息——写完立即提交并释放连接
        TurnContext ctx = txTemplate.execute(status -> prepareTurn(userId, sessionId, text));

        // ② 事务外：检索知识库 + 调模型（慢操作，绝不占数据库连接）
        List<RetrievedChunk> hits = retrieve(text, userId);
        List<AgentSkill> skills = agentSkillFactory.forUser(userId);
        AiChatResult result = callModel(buildTurns(ctx, hits, skills, text), skills);

        // ③ 短事务：落助手消息、写引用与用量、更新会话时间
        return txTemplate.execute(status -> finishTurn(ctx, hits, result));
    }

    /** 第一段事务：会话定位/创建 + 历史读取 + 用户消息落库 */
    private TurnContext prepareTurn(Long userId, Long sessionId, String text) {
        ChatSession session;
        if (sessionId == null) {
            session = sessions.save(ChatSession.create(userId, defaultTitle(text)));
        } else {
            session = sessions.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.SESSION_NOT_FOUND, "会话不存在"));
        }
        List<ChatMessage> history = recentHistory(session.getId());
        messages.save(ChatMessage.create(session.getId(), userId, MessageRole.USER, text));
        return new TurnContext(session.getId(), userId, history);
    }

    /** 第三段事务：助手回复落库 + 会话时间刷新 */
    private ChatReply finishTurn(TurnContext ctx, List<RetrievedChunk> hits, AiChatResult result) {
        ChatMessage assistantMessage = ChatMessage.withReferences(
                ctx.sessionId(), ctx.userId(), MessageRole.ASSISTANT, result.text(), toReferencesJson(hits));
        assistantMessage.setUsageJson(toUsageJson(result));
        ChatMessage saved = messages.save(assistantMessage);
        sessions.findById(ctx.sessionId()).ifPresent(session -> {
            session.touch();
            sessions.save(session);
        });
        return new ChatReply(ctx.sessionId(), saved.getId(), result.text(), toReferences(hits));
    }

    /**
     * 取该会话<b>最近</b> {@value #HISTORY_SIZE} 条历史，返回升序。
     *
     * <p>必须"倒序取 N 条再反转"：用 {@code OrderByIdAsc + PageRequest.of(0, N)} 拿到的是
     * 会话<b>最早</b>的 N 条，会话一旦超过 N 条消息，模型上下文就永久冻结在开头，
     * 后面的新消息全部看不见。
     */
    private List<ChatMessage> recentHistory(Long sessionId) {
        List<ChatMessage> newestFirst = messages.findBySessionIdOrderByIdDesc(
                sessionId, PageRequest.of(0, HISTORY_SIZE));
        List<ChatMessage> ascending = new ArrayList<>(newestFirst);
        Collections.reverse(ascending);
        return ascending;
    }

    /** 组装送给模型的完整消息序列：System(人设+资料+工具规则) + 历史 + 当前问题 */
    private List<ChatTurn> buildTurns(TurnContext ctx, List<RetrievedChunk> hits,
                                      List<AgentSkill> skills, String text) {
        String systemPrompt = AIPrompts.tutorSystem(hits)
                + (skills.isEmpty() ? "" : AIPrompts.toolGuide(skills));

        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn(TurnRole.SYSTEM, systemPrompt));
        for (ChatMessage m : ctx.history()) {
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
        return turns;
    }

    /**
     * 检索个人知识库：优先向量检索（按用户隔离）；失败或无命中时降级为关键词检索，
     * 保证"没有 Embedding 能力/额度"时 RAG 仍能工作（诚实降级而非整链路失败）。
     */
    private List<RetrievedChunk> retrieve(String query, Long userId) {
        int k = props.getRag().getTopK();
        try {
            List<RetrievedChunk> hits = vectorIndex.search(query, k, props.getRag().getMinScore(), userId);
            if (!hits.isEmpty()) {
                return hits;
            }
            log.info("向量检索无命中，降级为关键词检索");
        } catch (Exception e) {
            log.warn("向量检索失败，降级为关键词检索: {}", e.getMessage());
        }
        try {
            return keywordSearch.search(userId, query, k);
        } catch (Exception e) {
            log.warn("关键词降级检索也失败，将无上下文回答: {}", e.getMessage());
            return List.of();
        }
    }

    private AiChatResult callModel(List<ChatTurn> turns, List<AgentSkill> skills) {
        try {
            return aiChat.chat(turns, skills);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 细节只进日志（可能含上游 URL/响应体），对外给可操作的提示
            log.error("AI 模型调用失败", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE,
                    "AI 服务暂时不可用，请稍后重试；若持续失败请检查 AI_BASE_URL / AI_API_KEY / AI_CHAT_MODEL 配置");
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
