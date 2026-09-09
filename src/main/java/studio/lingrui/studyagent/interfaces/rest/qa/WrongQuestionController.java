package studio.lingrui.studyagent.interfaces.rest.qa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.qa.WrongItem;
import studio.lingrui.studyagent.application.qa.WrongQuestionService;
import studio.lingrui.studyagent.domain.qa.ReviewStatus;
import studio.lingrui.studyagent.domain.qa.WrongQuestion;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

/**
 * 错题整理。
 */
@RestController
@RequestMapping("/api/wrongs")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final WrongQuestionService wrongService;

    @PostMapping
    public ApiResponse<WrongQuestion> add(@Valid @RequestBody AddWrongPayload payload) {
        return ApiResponse.ok(wrongService.addWrong(UserContext.getUserId(),
                payload.questionId(), payload.userAnswer()));
    }

    @GetMapping
    public ApiResponse<PageResult<WrongItem>> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(wrongService.list(UserContext.getUserId(), status, page, size));
    }

    @GetMapping("/pending-count")
    public ApiResponse<Long> pendingCount() {
        return ApiResponse.ok(wrongService.pendingCount(UserContext.getUserId()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> markStatus(@PathVariable Long id, @Valid @RequestBody StatusPayload payload) {
        wrongService.markStatus(UserContext.getUserId(), id, payload.status());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        wrongService.delete(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    public record AddWrongPayload(@NotNull Long questionId, @NotBlank String userAnswer) {
    }

    public record StatusPayload(@NotNull ReviewStatus status) {
    }
}
