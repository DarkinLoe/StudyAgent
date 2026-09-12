package studio.lingrui.studyagent.infrastructure.mq;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;
import studio.lingrui.studyagent.infrastructure.mq.message.DocumentIngestMessage;
import studio.lingrui.studyagent.infrastructure.mq.message.ReminderPushMessage;

/**
 * RabbitMQ 发布门面（study-agent.mq.enabled=true 时启用）：
 * <ul>
 *   <li>文档摄入任务 → ingest 队列（消息体见 {@link DocumentIngestMessage}）</li>
 *   <li>学习提醒 → reminder 队列（消息体见 {@link ReminderPushMessage}）</li>
 *   <li>发送失败仅记日志，不阻塞主流程（业务侧已有同步回退与状态机兜底）</li>
 *   <li>开启 publisher confirm / return 回调：Broker 未确认或消息无法路由时能立刻在日志里看到，
 *       而不是"以为发出去了"</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class MqGateway {

    private final RabbitTemplate rabbitTemplate;
    private final StudyAgentProperties props;

    @PostConstruct
    void initCallbacks() {
        // 消息无法路由到任何队列时回调（需要 spring.rabbitmq.publisher-returns=true）
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "消息无法路由（无匹配队列）exchange={} routingKey={} reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        // Broker 确认回调（需要 spring.rabbitmq.publisher-confirm-type=correlated）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息未被 Broker 确认（可能已丢失）cause={}", cause);
            }
        });
    }

    public void publishDocumentIngest(Long docId) {
        publish(props.getMq().getIngestQueue(), new DocumentIngestMessage(docId));
    }

    public void publishReminderPush(Long userId, Long reminderId, String title, String message) {
        publish(props.getMq().getReminderQueue(),
                new ReminderPushMessage(userId, reminderId, title, message));
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
