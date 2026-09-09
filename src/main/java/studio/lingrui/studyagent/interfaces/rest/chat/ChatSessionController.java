package studio.lingrui.studyagent.interfaces.rest.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.chat.ChatService;
import studio.lingrui.studyagent.domain.chat.ChatMessage;
import studio.lingrui.studyagent.domain.chat.ChatSession;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

import java.util.List;

/**
 * 问答会话管理。
 */
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatService chatService;

    @GetMapping
    public ApiResponse<PageResult<ChatSession>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ChatSession> sessions = chatService.listSessions(UserContext.getUserId(), page, size);
        return ApiResponse.ok(PageResult.of(sessions));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<ChatMessage>> history(@PathVariable Long id) {
        return ApiResponse.ok(chatService.history(UserContext.getUserId(), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ChatSession> rename(@PathVariable Long id, @Valid @RequestBody RenameRequest request) {
        return ApiResponse.ok(chatService.rename(UserContext.getUserId(), id, request.title()));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        chatService.archive(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    public record RenameRequest(@NotBlank String title) {
    }
}
