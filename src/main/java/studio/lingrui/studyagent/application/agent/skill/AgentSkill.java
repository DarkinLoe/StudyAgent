package studio.lingrui.studyagent.application.agent.skill;

/**
 * Agent 技能（工具）契约：一次"模型可以请求执行的动作"。
 *
 * <p>设计要点：
 * <ul>
 *   <li>应用层只依赖本接口，不依赖任何 LLM 框架类型（框架适配在 infrastructure）；</li>
 *   <li>参数以 JSON 字符串传递，契约由 {@link #parametersJsonSchema()} 描述给模型；</li>
 *   <li>{@link #description()} 是写给模型看的"什么时候用/什么时候不要用"；</li>
 *   <li>{@link #execute(String)} 不允许抛异常穿透工具循环——失败也要返回文本，让模型看得见。</li>
 * </ul>
 */
public interface AgentSkill {

    /** 工具名（模型输出 tool_call 时引用） */
    String name();

    /** 工具用途说明（写给模型：何时调用、何时不要调用） */
    String description();

    /** 参数 JSON Schema（写给模型：怎么填参数） */
    String parametersJsonSchema();

    /**
     * 执行技能。
     *
     * @param argumentsJson 模型给出的参数 JSON 字符串，可能为空串
     * @return 结果文本（成功或失败的说明，都会回填给模型）
     */
    String execute(String argumentsJson);
}
