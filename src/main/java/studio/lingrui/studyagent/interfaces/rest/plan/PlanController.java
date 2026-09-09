package studio.lingrui.studyagent.interfaces.rest.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.plan.PlanApplicationService;
import studio.lingrui.studyagent.application.plan.TodayTask;
import studio.lingrui.studyagent.domain.plan.PlanTask;
import studio.lingrui.studyagent.domain.plan.StudyPlan;
import studio.lingrui.studyagent.domain.plan.StudyPlanStatus;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 学习计划与计划任务。
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanApplicationService planService;

    @PostMapping
    public ApiResponse<StudyPlan> create(@Valid @RequestBody PlanPayload payload) {
        return ApiResponse.ok(planService.createPlan(UserContext.getUserId(),
                payload.title(), payload.description(), payload.startDate(), payload.endDate()));
    }

    @GetMapping
    public ApiResponse<PageResult<StudyPlan>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<StudyPlan> plans = planService.listPlans(UserContext.getUserId(), page, size);
        return ApiResponse.ok(PageResult.of(plans));
    }

    @GetMapping("/today")
    public ApiResponse<List<TodayTask>> today() {
        return ApiResponse.ok(planService.todayTasks(UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudyPlan> detail(@PathVariable Long id) {
        return ApiResponse.ok(planService.detailPlan(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudyPlan> update(@PathVariable Long id, @Valid @RequestBody PlanUpdatePayload payload) {
        return ApiResponse.ok(planService.updatePlan(UserContext.getUserId(), id,
                payload.title(), payload.description(), payload.startDate(), payload.endDate(),
                payload.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        planService.deletePlan(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    // ---------------- 任务 ----------------

    @GetMapping("/{planId}/tasks")
    public ApiResponse<List<PlanTask>> tasks(@PathVariable Long planId) {
        return ApiResponse.ok(planService.planTasks(UserContext.getUserId(), planId));
    }

    @PostMapping("/{planId}/tasks")
    public ApiResponse<PlanTask> addTask(@PathVariable Long planId, @Valid @RequestBody TaskPayload payload) {
        return ApiResponse.ok(planService.addTask(UserContext.getUserId(), planId,
                payload.subject(), payload.content(),
                payload.plannedDate(), payload.plannedStart(), payload.plannedMinutes()));
    }

    @PutMapping("/tasks/{taskId}")
    public ApiResponse<PlanTask> updateTask(@PathVariable Long taskId, @Valid @RequestBody TaskPayload payload) {
        return ApiResponse.ok(planService.updateTask(UserContext.getUserId(), taskId,
                payload.subject(), payload.content(),
                payload.plannedDate(), payload.plannedStart(), payload.plannedMinutes()));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ApiResponse<Void> completeTask(@PathVariable Long taskId) {
        planService.completeTask(UserContext.getUserId(), taskId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(@PathVariable Long taskId) {
        planService.deleteTask(UserContext.getUserId(), taskId);
        return ApiResponse.ok();
    }

    // ---------------- DTO ----------------

    public record PlanPayload(@NotBlank String title, String description,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    }

    public record PlanUpdatePayload(@NotBlank String title, String description,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                    StudyPlanStatus status) {
    }

    public record TaskPayload(String subject,
                              @NotBlank String content,
                              @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedDate,
                              @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime plannedStart,
                              @NotNull Integer plannedMinutes) {
    }
}
