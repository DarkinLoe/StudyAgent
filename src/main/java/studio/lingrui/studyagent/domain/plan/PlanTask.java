package studio.lingrui.studyagent.domain.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 计划任务：某天某时段学什么（学习提示的原子单位）。
 */
@Getter
@Setter
@Entity
@Table(name = "plan_task", indexes = {
        @Index(name = "idx_plan_task_plan", columnList = "plan_id"),
        @Index(name = "idx_plan_task_date", columnList = "planned_date")
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlanTask extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 课程/科目，如「线性代数」「英语六级」 */
    @Column(nullable = false, length = 100)
    private String subject;

    /** 学习内容描述 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(name = "planned_start", nullable = false)
    private LocalTime plannedStart;

    @Column(name = "planned_minutes", nullable = false)
    private Integer plannedMinutes;

    @Column(nullable = false)
    private Boolean done = Boolean.FALSE;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 是否已触发过学习提醒 */
    @Column(name = "reminder_sent", nullable = false)
    private Boolean reminderSent = Boolean.FALSE;

    public static PlanTask create(Long planId, String subject, String content,
                                  LocalDate plannedDate, LocalTime plannedStart, int plannedMinutes) {
        PlanTask task = new PlanTask();
        task.setPlanId(planId);
        task.setSubject(subject);
        task.setContent(content);
        task.setPlannedDate(plannedDate);
        task.setPlannedStart(plannedStart);
        task.setPlannedMinutes(plannedMinutes);
        task.setDone(false);
        task.setReminderSent(false);
        return task;
    }

    /** 计划开始时刻（日期+时间） */
    public LocalDateTime scheduledAt() {
        return LocalDateTime.of(plannedDate, plannedStart);
    }

    public void markDone() {
        this.done = true;
        this.completedAt = LocalDateTime.now();
    }

    public void markReminderSent() {
        this.reminderSent = true;
    }
}
