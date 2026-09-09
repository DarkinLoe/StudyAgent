package studio.lingrui.studyagent.interfaces.rest.knowledge;

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
import studio.lingrui.studyagent.application.knowledge.NoteApplicationService;
import studio.lingrui.studyagent.domain.knowledge.StudyNote;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

/**
 * 知识管理：学习笔记（手动 / Agent 自动整理）。
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteApplicationService noteService;

    @PostMapping
    public ApiResponse<StudyNote> createManual(@Valid @RequestBody NotePayload payload) {
        return ApiResponse.ok(noteService.createManual(UserContext.getUserId(),
                payload.title(), payload.content(), payload.tagsJson()));
    }

    @GetMapping
    public ApiResponse<PageResult<StudyNote>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<StudyNote> notes = noteService.list(UserContext.getUserId(), page, size);
        return ApiResponse.ok(PageResult.of(notes));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudyNote> detail(@PathVariable Long id) {
        return ApiResponse.ok(noteService.detail(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudyNote> update(@PathVariable Long id, @Valid @RequestBody NotePayload payload) {
        return ApiResponse.ok(noteService.update(UserContext.getUserId(), id,
                payload.title(), payload.content(), payload.tagsJson()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        noteService.delete(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/auto-from-session")
    public ApiResponse<StudyNote> autoFromSession(@Valid @RequestBody AutoSessionPayload payload) {
        return ApiResponse.ok(noteService.autoFromSession(UserContext.getUserId(),
                payload.sessionId(), payload.title()));
    }

    @PostMapping("/auto-from-document")
    public ApiResponse<StudyNote> autoFromDocument(@Valid @RequestBody AutoDocumentPayload payload) {
        return ApiResponse.ok(noteService.autoFromDocument(UserContext.getUserId(),
                payload.docId(), payload.title()));
    }

    public record NotePayload(@NotBlank String title, @NotBlank String content, String tagsJson) {
    }

    public record AutoSessionPayload(@NotNull Long sessionId, String title) {
    }

    public record AutoDocumentPayload(@NotNull Long docId, String title) {
    }
}
