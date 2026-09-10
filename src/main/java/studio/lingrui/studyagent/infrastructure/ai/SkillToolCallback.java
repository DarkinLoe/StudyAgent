package studio.lingrui.studyagent.infrastructure.ai;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import studio.lingrui.studyagent.application.agent.skill.AgentSkill;

/**
 * 适配器：把应用层的 {@link AgentSkill} 翻译成 Spring AI 的 {@link ToolCallback}。
 *
 * <p>这是"应用层不依赖框架"的落点——只有本类知道 Spring AI 的 ToolDefinition/ToolCallback 长什么样。
 */
public final class SkillToolCallback implements ToolCallback {

    private final AgentSkill skill;

    public SkillToolCallback(AgentSkill skill) {
        this.skill = skill;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return new SimpleToolDefinition(skill.name(), skill.description(), skill.parametersJsonSchema());
    }

    @Override
    public String call(String toolInput) {
        return skill.execute(toolInput);
    }

    /** ToolDefinition 是接口，record 天然实现其 name()/description()/inputSchema() */
    private record SimpleToolDefinition(String name, String description, String inputSchema)
            implements ToolDefinition {
    }
}
