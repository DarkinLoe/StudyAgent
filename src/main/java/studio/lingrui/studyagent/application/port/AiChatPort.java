package studio.lingrui.studyagent.application.port;

import studio.lingrui.studyagent.application.agent.skill.AgentSkill;

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
     * 带技能（工具）的对话：模型可在循环中请求执行技能，实现方负责执行、结果回填与兜底。
     *
     * @param skills 本次请求可用的技能（按用户上下文构建），可为空
     */
    AiChatResult chat(List<ChatTurn> turns, List<AgentSkill> skills);

    /**
     * 单轮简单对话（如自动生成笔记/学习分析时的内部调用）。
     */
    String ask(String systemPrompt, String userContent);
}
