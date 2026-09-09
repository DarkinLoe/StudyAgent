package studio.lingrui.studyagent.domain.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

import java.time.LocalDate;

/**
 * 学习计划（聚合根）：一个时间段内的学习安排。
 */
@Getter
@Setter
@Entity
@Table(name = "study_plan")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StudyPlan extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudyPlanStatus status = StudyPlanStatus.PLANNING;

    public static StudyPlan create(Long userId, String title, String description,
                                   LocalDate startDate, LocalDate endDate) {
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setTitle(title);
        plan.setDescription(description);
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setStatus(StudyPlanStatus.PLANNING);
        return plan;
    }

    public void update(String title, String description, LocalDate startDate, LocalDate endDate) {
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void changeStatus(StudyPlanStatus status) {
        this.status = status;
    }
}
