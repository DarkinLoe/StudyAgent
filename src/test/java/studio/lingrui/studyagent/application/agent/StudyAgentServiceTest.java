package studio.lingrui.studyagent.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import studio.lingrui.studyagent.application.agent.skill.AgentSkill;
import studio.lingrui.studyagent.application.agent.skill.AgentSkillFactory;
import studio.lingrui.studyagent.application.chat.ChatReply;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.application.port.AiChatResult;
import studio.lingrui.studyagent.application.port.ChatTurn;
import studio.lingrui.studyagent.application.port.TurnRole;
import studio.lingrui.studyagent.application.port.VectorIndexPort;
import studio.lingrui.studyagent.application.rag.KnowledgeKeywordSearchService;
import studio.lingrui.studyagent.domain.chat.ChatMessage;
import studio.lingrui.studyagent.domain.chat.ChatMessageRepository;
import studio.lingrui.studyagent.domain.chat.ChatSession;
import studio.lingrui.studyagent.domain.chat.ChatSessionRepository;
import studio.lingrui.studyagent.domain.chat.MessageRole;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;
import studio.lingrui.studyagent.domain.rag.DocumentSourceType;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单 Agent 编排的两条关键行为：
 * ① 会话历史必须取"最近 N 条"并按时间升序拼给模型（曾误用 OrderByIdAsc 取成最早 N 条）；
 * ② 检索降级链：向量检索可用则用向量结果，抛异常/无命中才退到关键词检索。
 *
 * <p>这里用"直接执行回调"的事务管理器代替真实事务——本测试只关心编排逻辑，
 * 不关心事务传播（事务边界由 TransactionTemplate 显式圈定，见 StudyAgentService 类注释）。
 */
class StudyAgentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 7L;

    private ChatSessionRepository sessions;
    private ChatMessageRepository messages;
    private VectorIndexPort vectorIndex;
    private KnowledgeKeywordSearchService keywordSearch;
    private AiChatPort aiChat;
    private AgentSkillFactory skillFactory;
    private StudyAgentService service;

    @BeforeEach
    void setUp() {
        sessions = mock(ChatSessionRepository.class);
        messages = mock(ChatMessageRepository.class);
        vectorIndex = mock(VectorIndexPort.class);
        keywordSearch = mock(KnowledgeKeywordSearchService.class);
        aiChat = mock(AiChatPort.class);
        skillFactory = mock(AgentSkillFactory.class);

        StudyAgentProperties props = new StudyAgentProperties();
        when(skillFactory.forUser(anyLong())).thenReturn(List.of());
        when(sessions.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiChat.chat(any(), any())).thenReturn(new AiChatResult("这是回答", "test-model", 10L, 5L));

        service = new StudyAgentService(sessions, messages, vectorIndex, keywordSearch,
                aiChat, skillFactory, props, new ObjectMapper(), alwaysCommitTransactions());
    }

    @Test
    void historyIsTakenFromTheNewestMessagesAndReplayedOldestFirst() {
        when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(existingSession()));
        // 仓储按"倒序"返回最近两条：最新在前
        ChatMessage newest = ChatMessage.create(SESSION_ID, USER_ID, MessageRole.ASSISTANT, "第二答");
        ChatMessage older = ChatMessage.create(SESSION_ID, USER_ID, MessageRole.USER, "第二问");
        when(messages.findBySessionIdOrderByIdDesc(eq(SESSION_ID), any()))
                .thenReturn(List.of(newest, older));
        when(vectorIndex.search(anyString(), anyInt(), anyDouble(), anyLong())).thenReturn(List.of());

        service.send(USER_ID, SESSION_ID, "第三问");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiChat).chat(captor.capture(), any());
        List<ChatTurn> turns = captor.getValue();

        assertEquals(TurnRole.SYSTEM, turns.get(0).role());
        assertEquals(TurnRole.USER, turns.get(1).role());
        assertEquals("第二问", turns.get(1).content(), "历史应按时间升序回放");
        assertEquals(TurnRole.ASSISTANT, turns.get(2).role());
        assertEquals("第二答", turns.get(2).content());
        assertEquals(TurnRole.USER, turns.get(3).role());
        assertEquals("第三问", turns.get(3).content());

        // 回归守卫：绝不能再退化成"按 id 升序取前 N 条"（那会永远只看到最早的消息）
        verify(messages, never()).findBySessionIdOrderByIdAsc(any(), any());
    }

    @Test
    void vectorHitsAreUsedWhenAvailable() {
        stubExistingSession();
        List<RetrievedChunk> hits = List.of(new RetrievedChunk(9L, "课件.pdf",
                DocumentSourceType.OTHER, 0.9, "向量命中片段"));
        when(vectorIndex.search(anyString(), anyInt(), anyDouble(), anyLong())).thenReturn(hits);

        ChatReply reply = service.send(USER_ID, SESSION_ID, "问题");

        assertEquals(1, reply.references().size());
        assertEquals("课件.pdf", reply.references().get(0).docName());
        verify(keywordSearch, never()).search(anyLong(), anyString(), anyInt());
    }

    @Test
    void fallsBackToKeywordSearchWhenVectorSearchFails() {
        stubExistingSession();
        when(vectorIndex.search(anyString(), anyInt(), anyDouble(), anyLong()))
                .thenThrow(new IllegalStateException("embedding 额度不足"));
        when(keywordSearch.search(eq(USER_ID), anyString(), anyInt())).thenReturn(List.of(
                new RetrievedChunk(11L, "笔记.md", DocumentSourceType.OTHER, 0.5, "关键词命中片段")));

        ChatReply reply = service.send(USER_ID, SESSION_ID, "问题");

        assertEquals(1, reply.references().size());
        assertEquals("笔记.md", reply.references().get(0).docName());
    }

    @Test
    void fallsBackToKeywordSearchWhenVectorSearchReturnsNothing() {
        stubExistingSession();
        when(vectorIndex.search(anyString(), anyInt(), anyDouble(), anyLong())).thenReturn(List.of());
        when(keywordSearch.search(eq(USER_ID), anyString(), anyInt())).thenReturn(List.of(
                new RetrievedChunk(11L, "笔记.md", DocumentSourceType.OTHER, 0.5, "关键词命中片段")));

        ChatReply reply = service.send(USER_ID, SESSION_ID, "问题");

        assertNotNull(reply.references());
        assertEquals("笔记.md", reply.references().get(0).docName());
    }

    private void stubExistingSession() {
        when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(existingSession()));
        when(messages.findBySessionIdOrderByIdDesc(eq(SESSION_ID), any())).thenReturn(List.of());
    }

    /** 已存在会话（带 id：服务侧用 session.getId() 去查历史，id 为空会与 stub 对不上） */
    private ChatSession existingSession() {
        ChatSession session = ChatSession.create(USER_ID, "会话");
        session.setId(SESSION_ID);
        return session;
    }

    /** 不走真实事务：回调直接执行，便于在无 Spring 上下文时测编排逻辑 */
    private static TransactionTemplate alwaysCommitTransactions() {
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        return new TransactionTemplate(txManager);
    }
}
