package studio.lingrui.studyagent.application.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.application.agent.AIPrompts;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.domain.qa.Question;
import studio.lingrui.studyagent.domain.qa.QuestionType;
import studio.lingrui.studyagent.domain.rag.IndexStatus;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「从学习资料提取学习内容」之自动出题：基于已索引文档由 LLM 生成题目并写入题库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentQuestionService {

    private static final int DOC_CHAR_CAP = 8_000;

    private final KnowledgeDocumentRepository documents;
    private final AiChatPort aiChat;
    private final ObjectMapper objectMapper;
    private final QuestionService questionService;

    @Transactional
    public List<Question> generate(Long userId, Long docId) {
        KnowledgeDocument doc = documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
        if (doc.getStatus() != IndexStatus.INDEXED || doc.getTextContent() == null || doc.getTextContent().isBlank()) {
            throw new BizException(ErrorCode.DOC_NOT_INDEXED, "文档尚未完成索引，暂不能出题");
        }
        String excerpt = doc.getTextContent().length() <= DOC_CHAR_CAP
                ? doc.getTextContent() : doc.getTextContent().substring(0, DOC_CHAR_CAP);
        String raw;
        try {
            raw = aiChat.ask("你只输出合法 JSON，不要输出任何其他内容。", AIPrompts.questionsFromDocument(doc.getName(), excerpt));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 出题调用失败", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "AI 出题失败: " + e.getMessage(), e);
        }

        List<Question> created = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            for (JsonNode n : root.path("questions")) {
                try {
                    created.add(parseOne(userId, docId, n));
                } catch (Exception itemEx) {
                    log.warn("跳过一条无法解析的生成题目: {}", itemEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("AI 出题 JSON 解析失败: {}", raw, e);
            throw new BizException(ErrorCode.QUESTION_INVALID, "AI 返回的出题内容无法解析，请重试");
        }
        if (created.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "未能从资料中生成有效题目，请重试");
        }
        return created;
    }

    private Question parseOne(Long userId, Long docId, JsonNode n) throws Exception {
        QuestionType type = QuestionType.valueOf(n.path("type").asText("").toUpperCase(Locale.ROOT).trim());
        String stem = n.path("stem").asText("").trim();
        String answer = n.path("answer").asText("").trim();
        String explanation = n.has("explanation") ? n.path("explanation").asText("").trim() : null;
        int difficulty = n.path("difficulty").isInt() ? n.path("difficulty").asInt() : 3;
        String optionsJson = null;
        if (n.hasNonNull("options") && n.path("options").isArray()) {
            List<String> opts = new ArrayList<>();
            n.path("options").forEach(o -> opts.add(o.asText().trim()));
            optionsJson = objectMapper.writeValueAsString(opts);
        }
        String tagsJson = null;
        if (n.hasNonNull("tags") && n.path("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            n.path("tags").forEach(t -> tags.add(t.asText().trim()));
            tagsJson = objectMapper.writeValueAsString(tags);
        }
        if (stem.isEmpty() || answer.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "题目缺少题干或答案");
        }
        return questionService.create(userId, null, type, stem, optionsJson, answer,
                explanation, difficulty, tagsJson, docId);
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw.trim();
    }
}
