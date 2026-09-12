package studio.lingrui.studyagent.infrastructure.mq.message;

/**
 * 学习提醒推送事件（提醒落库后派发 → 消费端接推送通道，当前为日志占位）。
 */
public record ReminderPushMessage(Long userId, Long reminderId, String title, String message) {
}
