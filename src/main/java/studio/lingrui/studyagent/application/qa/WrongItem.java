package studio.lingrui.studyagent.application.qa;

/**
 * 错题列表展示项（错题 + 题目快照）。
 */
public record WrongItem(
        Long id,
        Long questionId,
        String type,
        String stem,
        String optionsJson,
        String expectedAnswer,
        String explanation,
        Integer difficulty,
        String tagsJson,
        String userAnswer,
        Integer mistakeCount,
        String lastWrongAt,
        String reviewStatus,
        java.time.LocalDateTime wrongCreatedAt
) {
}
