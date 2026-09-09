package studio.lingrui.studyagent.interfaces.rest.qa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.qa.QuestionBankService;
import studio.lingrui.studyagent.domain.qa.QuestionBank;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

/**
 * 题库管理。
 */
@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final QuestionBankService bankService;

    @PostMapping
    public ApiResponse<QuestionBank> create(@Valid @RequestBody BankPayload payload) {
        return ApiResponse.ok(bankService.create(UserContext.getUserId(),
                payload.name(), payload.description()));
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionBank>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<QuestionBank> banks = bankService.list(UserContext.getUserId(), page, size);
        return ApiResponse.ok(PageResult.of(banks));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionBank> detail(@PathVariable Long id) {
        return ApiResponse.ok(bankService.detail(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionBank> update(@PathVariable Long id, @Valid @RequestBody BankPayload payload) {
        return ApiResponse.ok(bankService.update(UserContext.getUserId(), id,
                payload.name(), payload.description()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bankService.delete(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    public record BankPayload(@NotBlank String name, String description) {
    }
}
