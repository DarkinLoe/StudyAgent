package studio.lingrui.studyagent.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.application.agent.skill.AgentSkill;
import studio.lingrui.studyagent.application.port.AiChatPort;
import studio.lingrui.studyagent.application.port.AiChatResult;
import studio.lingrui.studyagent.application.port.ChatTurn;
import studio.lingrui.studyagent.application.port.TurnRole;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM 适配器：通过 Spring AI ChatModel 调用 OpenAI 兼容端点（DeepSeek/SiliconFlow/OpenAI 等）。
 *
 * <p>带工具的对话采用<b>显式工具循环</b>（而非依赖框架隐藏的自动执行），便于观测与控制：
 * <ol>
 *   <li>模型返回 tool_call → 打日志 → 执行技能 → 把结果作为工具消息回填；</li>
 *   <li>循环上限 {@value #MAX_TOOL_ROUNDS} 轮，防止模型停不下来；</li>
 *   <li>相同工具+相同参数的重复调用会被识别并拒绝执行，提示模型直接回答；</li>
 *   <li>技能异常被转换为失败文本回填，循环不中断。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiChatAdapter implements AiChatPort {

    /** 工具循环上限：正常问答 1~2 轮足够，3 轮留余量 */
    private static final int MAX_TOOL_ROUNDS = 3;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Override
    public AiChatResult chat(List<ChatTurn> turns) {
        return chat(turns, List.of());
    }

    @Override
    public AiChatResult chat(List<ChatTurn> turns, List<AgentSkill> skills) {
        List<Message> messages = new ArrayList<>();
        for (ChatTurn turn : turns) {
            messages.add(toSpringAiMessage(turn));
        }

        if (skills == null || skills.isEmpty()) {
            return fromResponse(callModel(messages, null));
        }

        Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();
        List<ToolCallback> callbacks = new ArrayList<>();
        for (AgentSkill skill : skills) {
            ToolCallback callback = new SkillToolCallback(skill);
            callbacks.add(callback);
            callbacksByName.put(skill.name(), callback);
        }

        Set<String> executed = new HashSet<>();
        Long promptTokens = null;
        Long completionTokens = null;
        String model = null;
        ChatResponse response = null;

        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            response = callModel(messages, callbacks);
            ChatResponseMetadata metadata = response.getMetadata();
            if (metadata != null) {
                model = metadata.getModel() == null ? model : metadata.getModel();
                Usage usage = metadata.getUsage();
                if (usage != null) {
                    promptTokens = accumulate(promptTokens, usage.getPromptTokens());
                    completionTokens = accumulate(completionTokens, usage.getCompletionTokens());
                }
            }

            AssistantMessage output = response.getResult().getOutput();
            if (!output.hasToolCalls()) {
                log.info("工具循环在第 {} 轮结束（模型给出最终回答）", round);
                return new AiChatResult(output.getText(), model, promptTokens, completionTokens);
            }

            log.info("第 {} 轮工具调用: {}", round,
                    output.getToolCalls().stream().map(AssistantMessage.ToolCall::name).toList());

            // ① 把模型这次的 assistant(tool_calls) 消息原样带回
            messages.add(AssistantMessage.builder()
                    .content(output.getText() == null ? "" : output.getText())
                    .toolCalls(output.getToolCalls())
                    .build());

            // ② 逐个执行并回填工具结果
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                String key = toolCall.name() + "|"
                        + ToolArgsNormalizer.normalize(toolCall.arguments(), objectMapper);
                ToolCallback callback = callbacksByName.get(toolCall.name());
                String resultText;
                if (callback == null) {
                    resultText = "调用失败：未知工具 " + toolCall.name() + "。请基于现有信息直接回答用户。";
                } else if (!executed.add(key)) {
                    log.warn("检测到重复的工具调用 {}，已拒绝执行并提示模型收尾", key);
                    resultText = "该工具已用相同参数执行过，结果已在上下文中。请停止重复调用，直接回答用户。";
                } else {
                    resultText = safeExecute(callback, toolCall.arguments());
                }
                messages.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                toolCall.id(), toolCall.name(), resultText)))
                        .build());
            }
        }

        // ③ 轮数用尽：给出最后一次文本（若有），否则兜底话术
        log.warn("工具循环达到上限 {} 轮仍未得到最终回答", MAX_TOOL_ROUNDS);
        String lastText = extractText(response);
        if (lastText == null || lastText.isBlank()) {
            lastText = "抱歉，我尝试了多次仍未能完成这个请求。可以换个说法，或补充更多信息后重试。";
        }
        return new AiChatResult(lastText, model, promptTokens, completionTokens);
    }

    @Override
    public String ask(String systemPrompt, String userContent) {
        return chat(List.of(
                new ChatTurn(TurnRole.SYSTEM, systemPrompt),
                new ChatTurn(TurnRole.USER, userContent)), List.of()).text();
    }

    private ChatResponse callModel(List<Message> messages, List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return chatModel.call(new Prompt(messages));
        }
        return chatModel.call(new Prompt(messages,
                OpenAiChatOptions.builder().toolCallbacks(callbacks).build()));
    }

    /** 技能执行兜底：任何异常都转成可读文本，绝不让异常打断工具循环 */
    private String safeExecute(ToolCallback callback, String arguments) {
        try {
            String result = callback.call(arguments);
            return result == null ? "执行完成（无返回内容）。" : result;
        } catch (Exception e) {
            log.error("工具执行异常: {}", callback.getToolDefinition().name(), e);
            return "调用失败：工具执行异常，请勿重复调用。请向用户说明该操作暂时不可用。";
        }
    }

    private AiChatResult fromResponse(ChatResponse response) {
        ChatResponseMetadata metadata = response.getMetadata();
        String model = metadata == null ? null : metadata.getModel();
        Usage usage = metadata == null ? null : metadata.getUsage();
        Long promptTokens = usage == null || usage.getPromptTokens() == null
                ? null : usage.getPromptTokens().longValue();
        Long completionTokens = usage == null || usage.getCompletionTokens() == null
                ? null : usage.getCompletionTokens().longValue();
        String text = extractText(response);
        log.info("AI 调用完成 model={} prompt={} completion={}", model, promptTokens, completionTokens);
        return new AiChatResult(text, model, promptTokens, completionTokens);
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private Long accumulate(Long acc, Integer value) {
        if (value == null) {
            return acc;
        }
        return (acc == null ? 0L : acc) + value;
    }

    private Message toSpringAiMessage(ChatTurn turn) {
        return switch (turn.role()) {
            case SYSTEM -> new SystemMessage(turn.content());
            case USER -> new UserMessage(turn.content());
            case ASSISTANT -> new AssistantMessage(turn.content());
        };
    }
}
