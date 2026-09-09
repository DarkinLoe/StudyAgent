package studio.lingrui.studyagent.interfaces.rest.qa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import studio.lingrui.studyagent.application.qa.DocumentQuestionService;
import studio.lingrui.studyagent.application.qa.PracticeResult;
import studio.lingrui.studyagent.application.qa.QuestionService;
import studio.lingrui.studyagent.domain.qa.Question;
import studio.lingrui.studyagent.domain.qa.QuestionType;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

import java.util.List;

/**
 * 题库题目：增删改查 + 刷题判分 + 从知识库文档自动出题。
 * 约定：optionsJson/tagsJson 为 JSON 数组字符串（如 ["选项A","选项B"]）。
 */
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final DocumentQuestionService documentQuestionService;

    @PostMapping
    public ApiResponse<Question> create(@Valid @RequestBody QuestionPayload payload) {
        return ApiResponse.ok(questionService.create(
                UserContext.getUserId(), payload.bankId(), payload.type(), payload.stem(),
                payload.optionsJson(), payload.answer(), payload.explanation(),
                payload.difficulty(), payload.tagsJson(), payload.sourceDocId()));
    }

    @GetMapping
    public ApiResponse<PageResult<Question>> list(
            @RequestParam(required = false) Long bankId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Question> questions = questionService.list(UserContext.getUserId(), bankId, page, size);
        return ApiResponse.ok(PageResult.of(questions));
    }

    @GetMapping("/{id}")
    public ApiResponse<Question> detail(@PathVariable Long id) {
        return ApiResponse.ok(questionService.detail(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Question> update(@PathVariable Long id, @Valid @RequestBody QuestionPayload payload) {
        return ApiResponse.ok(questionService.update(
                UserContext.getUserId(), id, payload.bankId(), payload.type(), payload.stem(),
                payload.optionsJson(), payload.answer(), payload.explanation(),
                payload.difficulty(), payload.tagsJson()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/practice")
    public ApiResponse<PracticeResult> practice(@PathVariable Long id, @Valid @RequestBody PracticePayload payload) {
        return ApiResponse.ok(questionService.practice(UserContext.getUserId(), id, payload.userAnswer()));
    }

    @PostMapping("/from-document")
    public ApiResponse<List<Question>> fromDocument(@Valid @RequestBody FromDocumentPayload payload) {
        return ApiResponse.ok(documentQuestionService.generate(UserContext.getUserId(), payload.docId()));
    }

    public record QuestionPayload(Long bankId,
                                  @NotNull QuestionType type,
                                  @NotBlank String stem,
                                  String optionsJson,
                                  @NotBlank String answer,
                                  String explanation,
                                  Integer difficulty,
                                  String tagsJson,
                                  Long sourceDocId) {
    }

    public record PracticePayload(@NotBlank String userAnswer) {
    }

    public record FromDocumentPayload(@NotNull Long docId) {
    }
}
