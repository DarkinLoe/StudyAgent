package studio.lingrui.studyagent.application.qa;

/**
 * 刷题判分结果。
 */
public record PracticeResult(boolean correct, boolean autoJudged,
                             String explanation, String expectedAnswer) {
}
