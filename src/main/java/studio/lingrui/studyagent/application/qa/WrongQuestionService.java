package studio.lingrui.studyagent.application.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.qa.Question;
import studio.lingrui.studyagent.domain.qa.QuestionRepository;
import studio.lingrui.studyagent.domain.qa.ReviewStatus;
import studio.lingrui.studyagent.domain.qa.WrongQuestion;
import studio.lingrui.studyagent.domain.qa.WrongQuestionRepository;
import studio.lingrui.studyagent.application.port.CachePort;
import studio.lingrui.studyagent.shared.api.PageResult;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 错题本管理：列表（含题目快照）、新增/状态流转/删除；待复习数走 Redis 缓存。
 */
@Service
@RequiredArgsConstructor
public class WrongQuestionService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PENDING_CACHE_PREFIX = "cache:wrongPending:";

    private final WrongQuestionRepository wrongs;
    private final QuestionRepository questions;
    private final CachePort cache;

    public PageResult<WrongItem> list(Long userId, ReviewStatus status, int page, int size) {
        PageRequest pr = PageRequest.of(page - 1, size);
        Page<WrongQuestion> p;
        if (status == null) {
            p = wrongs.findByUserIdOrderByLastWrongAtDesc(userId, pr);
        } else {
            p = wrongs.findByUserIdAndReviewStatusOrderByLastWrongAtDesc(userId, status, pr);
        }
        if (p.isEmpty()) {
            return PageResult.empty(page, size);
        }
        List<Long> qids = p.getContent().stream().map(WrongQuestion::getQuestionId).toList();
        Map<Long, Question> byId = questions.findByIdIn(qids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<WrongItem> items = p.getContent().stream()
                .map(w -> toItem(w, byId.get(w.getQuestionId())))
                .toList();
        return new PageResult<>(items, p.getTotalElements(), page, size);
    }

    @Transactional
    public void markStatus(Long userId, Long wrongId, ReviewStatus status) {
        WrongQuestion w = require(userId, wrongId);
        w.markStatus(status);
        wrongs.save(w);
        evictPending(userId);
    }

    /**
     * 手动把某题记为错题（重复则累计答错次数）。
     */
    // 注意：故意不加 @Transactional —— 并发插入冲突后需要在独立事务中重新读取并累加，
    // 若包在同一事务里，捕获异常后 Session 已处于 rollback-only 状态，后续写入会失败
    public WrongQuestion addWrong(Long userId, Long questionId, String userAnswer) {
        questions.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.QUESTION_NOT_FOUND, "题目不存在"));
        WrongQuestion existing = wrongs.findByUserIdAndQuestionId(userId, questionId).orElse(null);
        WrongQuestion saved;
        if (existing == null) {
            try {
                saved = wrongs.save(WrongQuestion.firstWrong(userId, questionId, userAnswer));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // 并发插入同一 (userId, questionId)：唯一约束 uk_wrong_user_question 兜底，
                // 捕获后改为累加答错次数（放在独立事务里重新加载，避免脏 Session）
                WrongQuestion concurrent = wrongs.findByUserIdAndQuestionId(userId, questionId)
                        .orElseThrow(() -> e);
                concurrent.wrongAgain(userAnswer);
                saved = wrongs.save(concurrent);
            }
        } else {
            existing.wrongAgain(userAnswer);
            saved = wrongs.save(existing);
        }
        evictPending(userId);
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long wrongId) {
        WrongQuestion w = require(userId, wrongId);
        wrongs.delete(w);
        evictPending(userId);
    }

    public long pendingCount(Long userId) {
        String cacheKey = PENDING_CACHE_PREFIX + userId;
        Long cached = cache.get(cacheKey, Long.class).orElse(null);
        if (cached != null) {
            return cached;
        }
        long count = wrongs.countByUserIdAndReviewStatus(userId, ReviewStatus.PENDING);
        cache.put(cacheKey, count, java.time.Duration.ofMinutes(10));
        return count;
    }

    private WrongQuestion require(Long userId, Long wrongId) {
        return wrongs.findByIdAndUserId(wrongId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.WRONG_RECORD_NOT_FOUND, "错题记录不存在"));
    }

    private void evictPending(Long userId) {
        if (userId != null) {
            cache.evict(PENDING_CACHE_PREFIX + userId);
        }
    }

    private WrongItem toItem(WrongQuestion w, Question q) {
        return new WrongItem(
                w.getId(),
                w.getQuestionId(),
                q == null ? null : q.getType().name(),
                q == null ? "(题目已删除)" : q.getStem(),
                q == null ? null : q.getOptionsJson(),
                q == null ? null : q.getAnswer(),
                q == null ? null : q.getExplanation(),
                q == null ? null : q.getDifficulty(),
                q == null ? null : q.getTagsJson(),
                w.getUserAnswer(),
                w.getMistakeCount(),
                w.getLastWrongAt() == null ? null : w.getLastWrongAt().format(TS),
                w.getReviewStatus().name(),
                w.getCreatedAt()
        );
    }
}
