package studio.lingrui.studyagent.application.port;

import java.util.List;

/**
 * LLM 对话端口（由基础设施层对接 OpenAI 兼容端点实现）。
 */
public interface AiChatPort {

    /**
     * 以给定消息列表调用模型并返回结果（文本 + 用量元数据）。
     *
     * @param turns 会话消息（含 system 提示与历史）
     */
    AiChatResult chat(List<ChatTurn> turns);

    /**
     * 单轮简单对话（如自动生成笔记/学习分析时的内部调用）。
     */
    String ask(String systemPrompt, String userContent);
}
