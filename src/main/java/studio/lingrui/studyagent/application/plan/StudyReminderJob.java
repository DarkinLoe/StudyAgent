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

    @Scheduled(cron = "${study-agent.reminder.poll-cron}")
    public void remindDueTasks() {
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
                StudyReminder reminder = StudyReminder.create(
                        plan.getUserId(), plan.getId(), task.getId(), title, message);
                reminders.pushReminder(reminder);
                task.markReminderSent();
                tasks.save(task);
            } catch (Exception e) {
                log.error("学习提醒生成失败 taskId={}", task.getId(), e);
            }
        }
    }
}
