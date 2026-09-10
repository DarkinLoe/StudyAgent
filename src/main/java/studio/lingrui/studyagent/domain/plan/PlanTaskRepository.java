package studio.lingrui.studyagent.domain.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 计划任务仓储（领域接口）。
 */
public interface PlanTaskRepository {

    PlanTask save(PlanTask task);

    Optional<PlanTask> findById(Long id);

    Optional<PlanTask> findByIdAndPlanId(Long id, Long planId);

    List<PlanTask> findByPlanIdOrderByPlannedDateAscPlannedStartAsc(Long planId);

    /** 批量按计划 id 取任务（学习分析用） */
    List<PlanTask> findByPlanIdIn(List<Long> planIds);

    /** 计划日期 <= maxDate 且未完成且未提醒的任务，供学习提示调度器扫描 */
    List<PlanTask> findByDoneFalseAndReminderSentFalseAndPlannedDateLessThanEqual(LocalDate maxDate);

    long countByPlanId(Long planId);

    long countByPlanIdAndDoneTrue(Long planId);

    void delete(PlanTask task);
}
