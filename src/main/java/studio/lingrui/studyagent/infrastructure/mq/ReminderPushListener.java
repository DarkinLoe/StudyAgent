package studio.lingrui.studyagent.infrastructure.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 学习提醒消息消费者（占位：日志；后续可接入 WebSocket/邮件/短信推送通道）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class ReminderPushListener {

    @RabbitListener(queues = "${study-agent.mq.reminder-queue}")
    public void onReminder(Map<String, Object> body) {
        log.info("收到学习提醒推送事件: userId={}, title={}", body.get("userId"), body.get("title"));
    }
}
