package studio.lingrui.studyagent.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;

/**
 * 队列声明（仅启用 MQ 时创建）。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    private final StudyAgentProperties props;

    @Bean
    public Queue documentIngestQueue() {
        return QueueBuilder.durable(props.getMq().getIngestQueue()).build();
    }

    @Bean
    public Queue reminderPushQueue() {
        return QueueBuilder.durable(props.getMq().getReminderQueue()).build();
    }
}
