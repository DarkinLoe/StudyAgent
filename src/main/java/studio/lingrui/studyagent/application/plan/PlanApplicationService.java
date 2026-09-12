package studio.lingrui.studyagent.application.plan;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.plan.PlanTask;
import studio.lingrui.studyagent.domain.plan.PlanTaskRepository;
import studio.lingrui.studyagent.domain.plan.StudyPlan;
import studio.lingrui.studyagent.domain.plan.StudyPlanRepository;
import studio.lingrui.studyagent.domain.plan.StudyPlanStatus;
import studio.lingrui.studyagent.application.port.CachePort;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习计划与计划任务（应用服务）。
 * 「今日学习」结果缓存到 Redis（30s），任何计划/任务写操作都会失效对应缓存。
 */
@Service
@RequiredArgsConstructor
public class PlanApplicationService {

    private static final String TODAY_CACHE_PREFIX = "cache:todayTasks:";

    private final StudyPlanRepository plans;
    private final PlanTaskRepository tasks;
    private final CachePort cache;

    // ---------------- 计划 ----------------

    @Transactional
    public StudyPlan createPlan(Long userId, String title, String description,
                                LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        StudyPlan plan = plans.save(StudyPlan.create(userId, title, description, startDate, endDate));
        evictTodayCache();
        return plan;
    }

    public Page<StudyPlan> listPlans(Long userId, int page, int size) {
        return plans.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public StudyPlan detailPlan(Long userId, Long planId) {
        return requirePlan(userId, planId);
    }

    @Transactional
    public StudyPlan updatePlan(Long userId, Long planId, String title, String description,
                                LocalDate startDate, LocalDate endDate, StudyPlanStatus status) {
        validateRange(startDate, endDate);
        StudyPlan plan = requirePlan(userId, planId);
        plan.update(title, description, startDate, endDate);
        if (status != null) {
            plan.changeStatus(status);
        }
        StudyPlan saved = plans.save(plan);
        evictTodayCache();
        return saved;
    }

    @Transactional
    public void deletePlan(Long userId, Long planId) {
        StudyPlan plan = requirePlan(userId, planId);
        List<PlanTask> planTasks = tasks.findByPlanIdOrderByPlannedDateAscPlannedStartAsc(planId);
        planTasks.forEach(tasks::delete);
        plans.delete(plan);
        evictTodayCache();
    }

    // ---------------- 计划任务 ----------------

    public List<PlanTask> planTasks(Long userId, Long planId) {
        requirePlan(userId, planId);
        return tasks.findByPlanIdOrderByPlannedDateAscPlannedStartAsc(planId);
    }

    @Transactional
    public PlanTask addTask(Long userId, Long planId, String subject, String content,
                            LocalDate plannedDate, LocalTime plannedStart, Integer plannedMinutes) {
        StudyPlan plan = requirePlan(userId, planId);
        if (plannedDate == null || plannedStart == null || plannedMinutes == null || plannedMinutes <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任务日期/时间/时长必填且时长需为正数");
        }
        if (isBlank(content)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "学习内容不能为空");
        }
        if ((plan.getStartDate() != null && plannedDate.isBefore(plan.getStartDate()))
                || (plan.getEndDate() != null && plannedDate.isAfter(plan.getEndDate()))) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "任务日期应在计划区间内: " + plan.getStartDate() + " ~ " + plan.getEndDate());
        }
        PlanTask task = PlanTask.create(planId, isBlank(subject) ? "未分类" : subject,
                content, plannedDate, plannedStart, plannedMinutes);
        PlanTask saved = tasks.save(task);
        evictTodayCache();
        return saved;
    }

    @Transactional
    public PlanTask updateTask(Long userId, Long taskId, String subject, String content,
                               LocalDate plannedDate, LocalTime plannedStart, Integer plannedMinutes) {
        PlanTask task = requireOwnTask(userId, taskId);
        if (plannedDate != null) {
            task.setPlannedDate(plannedDate);
        }
        if (plannedStart != null) {
            task.setPlannedStart(plannedStart);
        }
        if (plannedMinutes != null && plannedMinutes > 0) {
            task.setPlannedMinutes(plannedMinutes);
        }
        if (!isBlank(subject)) {
            task.setSubject(subject);
        }
        if (!isBlank(content)) {
            task.setContent(content);
        }
        PlanTask saved = tasks.save(task);
        evictTodayCache();
        return saved;
    }

    @Transactional
    public void completeTask(Long userId, Long taskId) {
        PlanTask task = requireOwnTask(userId, taskId);
        task.markDone();
        tasks.save(task);
        evictTodayCache();
    }

    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        PlanTask task = requireOwnTask(userId, taskId);
        tasks.delete(task);
        evictTodayCache();
    }

    /**
     * 「今日学习」：今天的未完成任务 + 已逾期未完成的任务，按时间排序。结果 Redis 缓存 30s。
     */
    @Transactional(readOnly = true)
    public List<TodayTask> todayTasks(Long userId) {
        String cacheKey = TODAY_CACHE_PREFIX + userId;
        List<TodayTask> cached = cache.getList(cacheKey, TodayTask.class).orElse(null);
        if (cached != null) {
            return cached;
        }

        List<StudyPlan> userPlans = plans.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, 500))
                .getContent();
        if (userPlans.isEmpty()) {
            return List.of();
        }
        List<PlanTask> all = tasks.findByPlanIdIn(userPlans.stream().map(StudyPlan::getId).toList());
        Map<Long, StudyPlan> planById = userPlans.stream()
                .collect(Collectors.toMap(StudyPlan::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        List<TodayTask> result = new ArrayList<>();
        for (PlanTask t : all) {
            boolean isToday = today.equals(t.getPlannedDate());
            boolean overdueUndone = !t.getDone() && t.getPlannedDate().isBefore(today);
            if (isToday || overdueUndone) {
                StudyPlan plan = planById.get(t.getPlanId());
                result.add(new TodayTask(
                        plan == null ? null : plan.getId(),
                        plan == null ? "(未知计划)" : plan.getTitle(),
                        t.getId(),
                        t.getSubject(),
                        t.getContent(),
                        t.getPlannedDate(),
                        t.getPlannedStart(),
                        t.getPlannedMinutes(),
                        t.getDone(),
                        overdueUndone
                ));
            }
        }
        result.sort(Comparator.comparing(TodayTask::plannedStart));
        cache.put(cacheKey, result, Duration.ofSeconds(30));
        return result;
    }

    private StudyPlan requirePlan(Long userId, Long planId) {
        return plans.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.PLAN_NOT_FOUND, "学习计划不存在"));
    }

    private PlanTask requireOwnTask(Long userId, Long taskId) {
        PlanTask task = tasks.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.PLAN_TASK_NOT_FOUND, "计划任务不存在"));
        requirePlan(userId, task.getPlanId());
        return task;
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
    }

    private void evictTodayCache() {
        cache.evictByPrefix(TODAY_CACHE_PREFIX);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
