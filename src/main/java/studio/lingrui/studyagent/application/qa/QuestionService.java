package studio.lingrui.studyagent.application.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.qa.Question;
import studio.lingrui.studyagent.domain.qa.QuestionRepository;
import studio.lingrui.studyagent.domain.qa.QuestionType;
import studio.lingrui.studyagent.domain.qa.ReviewStatus;
import studio.lingrui.studyagent.domain.qa.WrongQuestion;
import studio.lingrui.studyagent.domain.qa.WrongQuestionRepository;
import studio.lingrui.studyagent.infrastructure.cache.RedisCacheHelper;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

/**
 * 题目管理 + 刷题判分（答错自动沉淀错题本；答对推进复习状态）。
 * 变更错题状态时同步失效「待复习错题数」的 Redis 缓存。
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    static final String PENDING_CACHE_PREFIX = "cache:wrongPending:";

    private final QuestionRepository questions;
    private final WrongQuestionRepository wrongs;
    private final RedisCacheHelper cache;

    @Transactional
    public Question create(Long userId, Long bankId, QuestionType type, String stem,
                           String optionsJson, String answer, String explanation,
                           Integer difficulty, String tagsJson, Long sourceDocId) {
        validate(type, stem, optionsJson, answer, true);
        Question q = Question.create(userId, bankId, type, stem, optionsJson, answer,
                explanation, difficulty, tagsJson, sourceDocId);
        return questions.save(q);
    }

    public Page<Question> list(Long userId, Long bankId, int page, int size) {
        if (bankId == null) {
            return questions.findByUserIdOrderByIdDesc(userId, PageRequest.of(page - 1, size));
        }
        return questions.findByUserIdAndBankIdOrderByIdDesc(userId, bankId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public Question detail(Long userId, Long questionId) {
        return questions.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.QUESTION_NOT_FOUND, "题目不存在"));
    }

    @Transactional
    public Question update(Long userId, Long questionId, Long bankId, QuestionType type, String stem,
                           String optionsJson, String answer, String explanation,
                           Integer difficulty, String tagsJson) {
        validate(type, stem, optionsJson, answer, true);
        Question q = detail(userId, questionId);
        q.setBankId(bankId);
        q.updateContent(type, stem, optionsJson, answer, explanation, difficulty, tagsJson);
        return questions.save(q);
    }

    @Transactional
    public void delete(Long userId, Long questionId) {
        Question q = detail(userId, questionId);
        questions.delete(q);
        wrongs.findByUserIdAndQuestionId(userId, questionId)
                .ifPresent(wrongs::delete);
        evictPending(userId);
    }

    /**
     * 刷题：判分，并按结果自动维护错题本（仅自动判定生效）。
     */
    @Transactional
    public PracticeResult practice(Long userId, Long questionId, String userAnswer) {
        Question q = detail(userId, questionId);
        QuestionGrader.JudgedResult judged = QuestionGrader.JudgedResult.of(false);
        boolean auto = QuestionGrader.judge(q.getType(), q.getAnswer(), userAnswer, judged);

        WrongQuestion existing = wrongs.findByUserIdAndQuestionId(userId, questionId).orElse(null);
        if (auto && !judged.correct) {
            if (existing == null) {
                wrongs.save(WrongQuestion.firstWrong(userId, questionId, userAnswer));
            } else {
                existing.wrongAgain(userAnswer);
                wrongs.save(existing);
            }
            evictPending(userId);
        } else if (auto && judged.correct && existing != null) {
            existing.reviewPassed();
            existing.markStatus(ReviewStatus.REVIEWED);
            wrongs.save(existing);
            evictPending(userId);
        }
        return new PracticeResult(judged.correct, auto, q.getExplanation(), q.getAnswer());
    }

    void evictPending(Long userId) {
        if (userId != null) {
            cache.delete(PENDING_CACHE_PREFIX + userId);
        }
    }

    private void validate(QuestionType type, String stem, String optionsJson, String answer,
                          boolean requiredAnswer) {
        if (type == null || isBlank(stem)) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "题型与题干不能为空");
        }
        if (requiredAnswer && isBlank(answer)) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "必须提供参考答案");
        }
        if ((type == QuestionType.SINGLE_CHOICE || type == QuestionType.MULTIPLE_CHOICE)
                && isBlank(optionsJson)) {
            throw new BizException(ErrorCode.QUESTION_INVALID, "选择题必须提供选项");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
