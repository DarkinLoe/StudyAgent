package studio.lingrui.studyagent.interfaces.rest.plan;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.plan.ReminderApplicationService;
import studio.lingrui.studyagent.domain.plan.StudyReminder;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 学习提醒（自动学习提示的落库结果，前端轮询/下拉即可）。
 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderApplicationService reminderService;

    @GetMapping
    public ApiResponse<PageResult<StudyReminder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<StudyReminder> reminders = reminderService.list(UserContext.getUserId(), page, size);
        return ApiResponse.ok(PageResult.of(reminders));
    }

    @GetMapping("/unread")
    public ApiResponse<List<StudyReminder>> unread() {
        return ApiResponse.ok(reminderService.unread(UserContext.getUserId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", reminderService.unreadCount(UserContext.getUserId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        reminderService.markRead(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        reminderService.markAllRead(UserContext.getUserId());
        return ApiResponse.ok();
    }
}
