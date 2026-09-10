package studio.lingrui.studyagent.application.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.lingrui.studyagent.application.plan.PlanApplicationService;
import studio.lingrui.studyagent.application.plan.TodayTask;

import java.util.List;

/**
 * 技能：查询今日/逾期的学习安排。
 *
 * <p>教学要点：结果文本要"写给模型读"——包含它能转述给学生的要点（科目、内容、时间、时长、状态），
 * 而不是只回一句"查询成功"。
 */
@Slf4j
@RequiredArgsConstructor
public class TodayPlanSkill implements AgentSkill {

    private final Long userId;
    private final PlanApplicationService planApplicationService;

    @Override
    public String name() {
        return "query_today_plan";
    }

    @Override
    public String description() {
        return "查询当前用户今天的学习任务以及已逾期未完成的任务。"
                + "当学生询问今天学什么、有什么安排、还欠哪些任务时调用。"
                + "与学习计划无关的问题不要调用。";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {}
                }
                """;
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            List<TodayTask> tasks = planApplicationService.todayTasks(userId);
            if (tasks.isEmpty()) {
                return "查询成功：今天没有学习任务，也没有逾期未完成的任务。";
            }
            StringBuilder sb = new StringBuilder("查询成功，共 ")
                    .append(tasks.size()).append(" 项安排：\n");
            for (TodayTask t : tasks) {
                sb.append("- ")
                        .append(Boolean.TRUE.equals(t.overdue()) ? "[已逾期] " : "[今日] ")
                        .append("计划《").append(t.planTitle()).append("》 ")
                        .append(t.subject()).append("：").append(t.content())
                        .append("，时间 ").append(t.plannedDate()).append(' ').append(t.plannedStart())
                        .append("，预计 ").append(t.plannedMinutes()).append(" 分钟")
                        .append("，状态：").append(Boolean.TRUE.equals(t.done()) ? "已完成" : "未完成")
                        .append('\n');
            }
            sb.append("请基于以上安排回答学生，并可在提示中给出时间建议。");
            return sb.toString();
        } catch (Exception e) {
            log.error("查询今日计划技能执行异常", e);
            return "调用失败：系统内部错误，请勿重试。请告知学生稍后再试。";
        }
    }
}
