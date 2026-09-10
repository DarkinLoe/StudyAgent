package studio.lingrui.studyagent.application.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.application.plan.PlanApplicationService;
import studio.lingrui.studyagent.application.qa.WrongQuestionService;

import java.util.List;

/**
 * 技能工厂：按请求级上下文（当前用户）组装可用技能列表。
 *
 * <p>教学要点：工具不是全局单例——它携带了"当前是谁在请求"这类上下文。
 * 以后接入登录体系后，用户身份应来自认证上下文（或框架的 ToolContext），而不是 ThreadLocal 默认值。
 */
@Component
@RequiredArgsConstructor
public class AgentSkillFactory {

    private final WrongQuestionService wrongQuestionService;
    private final PlanApplicationService planApplicationService;
    private final ObjectMapper objectMapper;

    public List<AgentSkill> forUser(Long userId) {
        return List.of(
                new RegisterWrongQuestionSkill(userId, wrongQuestionService, objectMapper),
                new TodayPlanSkill(userId, planApplicationService)
        );
    }
}
