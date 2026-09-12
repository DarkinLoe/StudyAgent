package studio.lingrui.studyagent.infrastructure.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.infrastructure.mq.message.ReminderPushMessage;

/**
 * 学习提醒消息消费者（占位：日志；后续可接入 WebSocket/邮件/短信推送通道）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class ReminderPushListener {

    @RabbitListener(queues = "${study-agent.mq.reminder-queue}")
    public void onReminder(ReminderPushMessage message) {
        if (message == null) {
            log.warn("收到空的提醒推送消息，已忽略");
            return;
        }
        log.info("收到学习提醒推送事件: userId={}, reminderId={}, title={}",
                message.userId(), message.reminderId(), message.title());
    }
}
