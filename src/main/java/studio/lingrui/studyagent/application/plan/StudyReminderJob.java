package studio.lingrui.studyagent.application.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.domain.plan.PlanTask;
import studio.lingrui.studyagent.domain.plan.PlanTaskRepository;
import studio.lingrui.studyagent.domain.plan.StudyPlan;
import studio.lingrui.studyagent.domain.plan.StudyPlanRepository;
import studio.lingrui.studyagent.domain.plan.StudyReminder;
import studio.lingrui.studyagent.application.port.CachePort;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习提示调度器：周期性扫描「到开始时间且未完成未提醒」的计划任务，
 * 生成 StudyReminder 并可经 MQ 推送。prompt 时间点由 plannedDate+plannedStart 决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyReminderJob {

    private final PlanTaskRepository tasks;
    private final StudyPlanRepository plans;
    private final ReminderApplicationService reminders;
    private final CachePort cache;
    private final StudyAgentProperties props;

    /**
     * 定时入口：多实例部署时用 Redis 分布式锁串行化，避免重复生成/推送提醒。
     * 锁 TTL 5 分钟（远大于单轮执行时间），执行完显式释放；Redis 不可用时放行（宁可重跑也不漏跑）。
     */
    @Scheduled(cron = "${study-agent.reminder.poll-cron}")
    public void remindDueTasks() {
        boolean useLock = props.getSecurity().isDistributedJobLock();
        String lockKey = "lock:job:study-reminder";
        // 锁句柄携带持有者 token：释放时做归属校验，避免"上一个实例的锁已超时被他人重新获取、
        // 却又被前一个实例删掉"
        CachePort.Lock lock = useLock
                ? cache.tryLock(lockKey, Duration.ofMinutes(5)).orElse(null)
                : null;
        if (useLock && lock == null) {
            log.info("其他实例正在执行学习提醒任务，跳过本轮");
            return;
        }
        try {
            doRemindDueTasks();
        } finally {
            cache.release(lock);
        }
    }

    private void doRemindDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<PlanTask> due = tasks.findByDoneFalseAndReminderSentFalseAndPlannedDateLessThanEqual(now.toLocalDate());
        if (due.isEmpty()) {
            return;
        }
        List<Long> planIds = due.stream().map(PlanTask::getPlanId).distinct().toList();
        Map<Long, StudyPlan> planMap = plans.findByIdIn(planIds).stream()
                .collect(Collectors.toMap(StudyPlan::getId, Function.identity()));

        for (PlanTask task : due) {
            if (Boolean.TRUE.equals(task.getDone()) || Boolean.TRUE.equals(task.getReminderSent())) {
                continue;
            }
            if (task.scheduledAt().isAfter(now)) {
                continue; // 未到开始时间
            }
            StudyPlan plan = planMap.get(task.getPlanId());
            if (plan == null) {
                continue;
            }
            try {
                boolean overdue = task.getPlannedDate().isBefore(now.toLocalDate());
                String title = "学习提醒 · " + task.getSubject();
                String message = (overdue ? "有学习安排已逾期，请尽快补上：\n" : "到学习时间啦：\n")
                        + "【" + task.getSubject() + "】" + task.getContent()
                        + "\n计划时段 " + task.getPlannedDate() + " " + task.getPlannedStart()
                        + "，预计 " + task.getPlannedMinutes() + " 分钟。";
                // 先落"已提醒"标记，再推送：多实例并发处理同一任务时，PlanTask 的 @Version
                // 乐观锁会让后提交者失败，于是只有一方能推送成功，不会发两条重复提醒
                // （标记成功后进程崩溃只会漏一条，不会重复发，取舍见 README 的已知边界）
                task.markReminderSent();
                tasks.save(task);
                reminders.pushReminder(StudyReminder.create(
                        plan.getUserId(), plan.getId(), task.getId(), title, message));
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                log.info("该任务已被其他实例提醒过，跳过 taskId={}", task.getId());
            } catch (Exception e) {
                log.error("学习提醒生成失败 taskId={}", task.getId(), e);
            }
        }
    }
}
