package studio.lingrui.studyagent.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;

import java.util.Map;

/**
 * RabbitMQ 发布门面（study-agent.mq.enabled=true 时启用）：
 * - 文档摄入任务 → ingest 队列
 * - 学习提醒 → reminder 队列
 * 发送失败仅记日志，不阻塞主流程（可后续接死信/重试）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class MqGateway {

    private final RabbitTemplate rabbitTemplate;
    private final StudyAgentProperties props;

    public void publishDocumentIngest(Long docId) {
        publish(props.getMq().getIngestQueue(), Map.of("docId", docId));
    }

    public void publishReminderPush(Long userId, Long reminderId, String title, String message) {
        publish(props.getMq().getReminderQueue(), Map.of(
                "userId", userId,
                "reminderId", reminderId,
                "title", title,
                "message", message));
    }

    private void publish(String queue, Object body) {
        try {
            rabbitTemplate.convertAndSend(queue, body);
            log.debug("消息已发布 queue={} body={}", queue, body);
        } catch (Exception e) {
            log.error("消息发布失败 queue={}", queue, e);
        }
    }
}
