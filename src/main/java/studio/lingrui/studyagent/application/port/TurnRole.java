package studio.lingrui.studyagent.application.port;

import java.util.List;

/**
 * 对话角色（应用层自身的消息模型，避免依赖具体 AI SDK 类型）。
 */
public enum TurnRole {
    SYSTEM,
    USER,
    ASSISTANT
}
