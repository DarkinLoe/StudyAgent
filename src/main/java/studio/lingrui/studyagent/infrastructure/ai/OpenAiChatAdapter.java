package studio.lingrui.studyagent.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.application.port.AiChatResult;
import studio.lingrui.studyagent.application.port.ChatTurn;
import studio.lingrui.studyagent.application.port.TurnRole;

import java.util.List;

/**
 * LLM 适配器：通过 Spring AI ChatModel 调用 OpenAI 兼容端点（DeepSeek/SiliconFlow/OpenAI 等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiChatAdapter implements AiChatPort {

    private final ChatModel chatModel;

    @Override
    public AiChatResult chat(List<ChatTurn> turns) {
        List<Message> messages = turns.stream()
                .map(this::toSpringAiMessage)
                .toList();
        ChatResponse response = chatModel.call(new Prompt(messages));

        ChatResponseMetadata meta = response.getMetadata();
        Usage usage = meta == null ? null : meta.getUsage();
        Long promptTokens = usage == null || usage.getPromptTokens() == null
                ? null : usage.getPromptTokens().longValue();
        Long completionTokens = usage == null || usage.getCompletionTokens() == null
                ? null : usage.getCompletionTokens().longValue();
        String text = response.getResult().getOutput().getText();
        String model = meta == null ? null : meta.getModel();

        log.info("AI 调用完成 model={} prompt={} completion={}",
                model, promptTokens, completionTokens);
        return new AiChatResult(text, model, promptTokens, completionTokens);
    }

    @Override
    public String ask(String systemPrompt, String userContent) {
        return chat(List.of(
                new ChatTurn(TurnRole.SYSTEM, systemPrompt),
                new ChatTurn(TurnRole.USER, userContent))).text();
    }

    private Message toSpringAiMessage(ChatTurn turn) {
        return switch (turn.role()) {
            case SYSTEM -> new SystemMessage(turn.content());
            case USER -> new UserMessage(turn.content());
            case ASSISTANT -> new AssistantMessage(turn.content());
        };
    }
}
