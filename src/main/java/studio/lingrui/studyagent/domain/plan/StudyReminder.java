package studio.lingrui.studyagent.domain.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 学习提醒记录：由调度器按计划任务生成，前端可读可标记已读。
 */
@Getter
@Setter
@Entity
@Table(name = "study_reminder", indexes = {
        @Index(name = "idx_reminder_user", columnList = "user_id"),
        @Index(name = "idx_reminder_task", columnList = "task_id")
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StudyReminder extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String message;

    @Column(name = "remind_at", nullable = false)
    private LocalDateTime remindAt;

    @Column(name = "read_flag", nullable = false)
    private Boolean readFlag = Boolean.FALSE;

    public static StudyReminder create(Long userId, Long planId, Long taskId,
                                       String title, String message) {
        StudyReminder r = new StudyReminder();
        r.setUserId(userId);
        r.setPlanId(planId);
        r.setTaskId(taskId);
        r.setTitle(title);
        r.setMessage(message);
        r.setRemindAt(LocalDateTime.now());
        r.setReadFlag(false);
        return r;
    }

    public void markRead() {
        this.readFlag = true;
    }
}
