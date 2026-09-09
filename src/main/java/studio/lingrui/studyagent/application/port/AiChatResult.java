package studio.lingrui.studyagent.application.port;

/**
 * LLM 单次对话结果：文本 + 元数据（模型名 / token 用量）。
 * 用于成本观测、上下文长度管理与后续评估。
 */
public record AiChatResult(
        String text,
        String model,
        Long promptTokens,
        Long completionTokens
) {

    public Long totalTokens() {
        if (promptTokens == null || completionTokens == null) {
            return null;
        }
        return promptTokens + completionTokens;
    }
}
