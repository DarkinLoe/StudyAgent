package studio.lingrui.studyagent.application.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.lingrui.studyagent.application.qa.WrongQuestionService;
import studio.lingrui.studyagent.domain.qa.WrongQuestion;
import studio.lingrui.studyagent.shared.exception.BizException;

/**
 * 技能：把题目记入错题本。
 *
 * <p>教学要点：本类只做"取参数 → 调应用服务 → 结果转文本"三件事，
 * 业务规则仍然在 {@link WrongQuestionService} 里，工具是胶水而不是第二份业务实现。
 */
@Slf4j
@RequiredArgsConstructor
public class RegisterWrongQuestionSkill implements AgentSkill {

    private final Long userId;
    private final WrongQuestionService wrongQuestionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "register_wrong_question";
    }

    @Override
    public String description() {
        return "把一道题目记入当前用户的错题本。"
                + "当学生明确表示某题做错、要求记录错题、或要求把刚才讲评的题收进错题本时调用。"
                + "纯知识讲解、闲聊、查询计划时不要调用。";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "questionId": {"type": "integer", "description": "题目 ID（题库中的编号）"},
                    "userAnswer": {"type": "string", "description": "学生当时的作答内容，如 \"A\" 或 \"我选B\""}
                  },
                  "required": ["questionId", "userAnswer"]
                }
                """;
    }

    @Override
    public String execute(String argumentsJson) {
        long questionId;
        String userAnswer;
        try {
            JsonNode node = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            if (!node.hasNonNull("questionId") || !node.hasNonNull("userAnswer")) {
                // 可恢复失败：把"缺什么"告诉模型，它有机会修正参数重试
                return "调用失败：缺少 questionId 或 userAnswer。请确认题目编号与学生作答后重试。";
            }
            questionId = node.get("questionId").asLong();
            userAnswer = node.get("userAnswer").asText();
        } catch (Exception e) {
            return "调用失败：参数不是合法 JSON。请用 {questionId, userAnswer} 重新调用。";
        }

        try {
            WrongQuestion wrong = wrongQuestionService.addWrong(userId, questionId, userAnswer);
            return "已成功记入错题本。题目ID: %d，学生作答: %s，累计答错: %d 次，当前复习状态: %s。"
                    .formatted(wrong.getQuestionId(), wrong.getUserAnswer(),
                            wrong.getMistakeCount(), wrong.getReviewStatus().name());
        } catch (BizException e) {
            // 业务性失败：题目不存在等 —— 明确给出下一步建议，避免模型原地重试
            return "调用失败：" + e.getMessage() + "。请向学生说明并请其确认题目编号，不要重复调用本工具。";
        } catch (Exception e) {
            // 不可恢复失败：明确要求模型不要重试
            log.error("记错题技能执行异常", e);
            return "调用失败：系统内部错误，请勿重试。请告知学生稍后再试。";
        }
    }
}
