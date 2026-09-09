package studio.lingrui.studyagent.interfaces.rest.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.agent.StudyAgentService;
import studio.lingrui.studyagent.application.chat.ChatReply;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;

/**
 * 单 Agent 问答入口：统一由「学习 Agent」回答（内部自动 RAG 检索）。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final StudyAgentService agent;

    @PostMapping("/chat")
    public ApiResponse<ChatReply> chat(@Valid @RequestBody ChatSendRequest request) {
        return ApiResponse.ok(agent.send(
                UserContext.getUserId(), request.sessionId(), request.content()));
    }

    public record ChatSendRequest(Long sessionId, @NotBlank String content) {
    }
}
