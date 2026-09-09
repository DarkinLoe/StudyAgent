package studio.lingrui.studyagent.application.port;

import java.util.List;

/**
 * 一条对话轮次。
 */
public record ChatTurn(TurnRole role, String content) {
}
