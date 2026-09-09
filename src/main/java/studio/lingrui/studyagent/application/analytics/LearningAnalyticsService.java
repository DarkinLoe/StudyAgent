package studio.lingrui.studyagent.application.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.application.agent.AIPrompts;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.domain.knowledge.StudyNoteRepository;
import studio.lingrui.studyagent.domain.plan.PlanTask;
import studio.lingrui.studyagent.domain.plan.PlanTaskRepository;
import studio.lingrui.studyagent.domain.plan.StudyPlan;
import studio.lingrui.studyagent.domain.plan.StudyPlanRepository;
import studio.lingrui.studyagent.domain.plan.StudyPlanStatus;
import studio.lingrui.studyagent.domain.qa.Question;
import studio.lingrui.studyagent.domain.qa.QuestionRepository;
import studio.lingrui.studyagent.domain.qa.ReviewStatus;
import studio.lingrui.studyagent.domain.qa.WrongQuestion;
import studio.lingrui.studyagent.domain.qa.WrongQuestionRepository;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习分析（应用服务）：统计 + 可选的 LLM 文字分析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningAnalyticsService {

    private final StudyPlanRepository plans;
    private final PlanTaskRepository tasks;
    private final WrongQuestionRepository wrongs;
    private final QuestionRepository questions;
    private final StudyNoteRepository notes;
    private final AiChatPort aiChat;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public LearningStats compute(Long userId) {
        long planTotal = plans.countByUserId(userId);
        long planActive = plans.countByUserIdAndStatus(userId, StudyPlanStatus.ACTIVE);

        List<Long> planIds = plans.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, 1000))
                .getContent().stream().map(StudyPlan::getId).toList();
        List<PlanTask> allTasks = planIds.isEmpty() ? List.of()
                : tasks.findByPlanIdIn(planIds);

        long taskTotal = allTasks.size();
        long taskDone = allTasks.stream().filter(t -> Boolean.TRUE.equals(t.getDone())).count();
        long plannedMinutes = allTasks.stream()
                .mapToLong(t -> t.getPlannedMinutes() == null ? 0 : t.getPlannedMinutes()).sum();
        long doneMinutes = allTasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getDone()))
                .mapToLong(t -> t.getPlannedMinutes() == null ? 0 : t.getPlannedMinutes()).sum();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long doneMinutesLast7 = allTasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getDone()) && t.getCompletedAt() != null
                        && t.getCompletedAt().isAfter(weekAgo))
                .mapToLong(t -> t.getPlannedMinutes() == null ? 0 : t.getPlannedMinutes()).sum();

        long wrongTotal = wrongs.countByUserId(userId);
        long wrongPending = wrongs.countByUserIdAndReviewStatus(userId, ReviewStatus.PENDING);
        long wrongReviewed = wrongs.countByUserIdAndReviewStatus(userId, ReviewStatus.REVIEWED);
        long wrongMastered = wrongs.countByUserIdAndReviewStatus(userId, ReviewStatus.MASTERED);

        long noteTotal = notes.countByUserId(userId);

        double completionRate = taskTotal == 0 ? 0
                : (double) taskDone / taskTotal;

        List<LearningStats.WeakTag> weakTags = weakTagsOfRecentWrongs(userId);

        return new LearningStats(planTotal, planActive, taskTotal, taskDone, completionRate,
                plannedMinutes, doneMinutes, doneMinutesLast7,
                wrongTotal, wrongPending, wrongReviewed, wrongMastered,
                noteTotal, weakTags);
    }

    /**
     * LLM 生成学习分析文字（可选能力）。
     */
    public String summarize(Long userId) {
        LearningStats stats = compute(userId);
        try {
            return aiChat.ask("你是一名学习规划分析师，输出简短 Markdown。",
                    AIPrompts.learningAnalysisSummary(stats.describe()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 学习分析失败", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "AI 学习分析失败: " + e.getMessage(), e);
        }
    }

    private List<LearningStats.WeakTag> weakTagsOfRecentWrongs(Long userId) {
        List<WrongQuestion> recent = wrongs.findByUserIdOrderByLastWrongAtDesc(userId, PageRequest.of(0, 100))
                .getContent();
        if (recent.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = recent.stream().map(WrongQuestion::getQuestionId).toList();
        Map<Long, Question> byId = questions.findByIdIn(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        Map<String, Long> tagCount = new LinkedHashMap<>();
        for (WrongQuestion w : recent) {
            Question q = byId.get(w.getQuestionId());
            if (q == null || q.getTagsJson() == null) {
                continue;
            }
            for (String tag : parseTags(q.getTagsJson())) {
                tagCount.merge(tag, 1L, Long::sum);
            }
        }
        return tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new LearningStats.WeakTag(e.getKey(), e.getValue()))
                .toList();
    }

    private List<String> parseTags(String tagsJson) {
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
