package studio.lingrui.studyagent.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;

/**
 * 队列与消息转换器声明（仅启用 MQ 时创建）。
 *
 * <p>三个关键点：
 * <ol>
 *   <li><b>JSON 序列化</b>：默认的 SimpleMessageConverter 走 JDK 序列化——消息不可读、
 *       与类结构强耦合、跨语言不可用；这里统一换成 JSON；</li>
 *   <li><b>死信兜底</b>：两个业务队列都挂了死信交换机。消费重试仍失败的消息会进
 *       {@value #DEAD_LETTER_QUEUE}，而不是被无限 requeue（毒消息会打满 CPU）
 *       或被 catch 掉后 ACK（等于静默丢任务）；</li>
 *   <li><b>信任包</b>：显式声明本项目的消息包，避免默认白名单导致自定义类型反序列化被拒。</li>
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    /** 死信交换机 */
    public static final String DEAD_LETTER_EXCHANGE = "study-agent.dlx";
    /** 死信队列：人工/后续补偿的入口 */
    public static final String DEAD_LETTER_QUEUE = "study-agent.dead-letter";

    private final StudyAgentProperties props;

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("studio.lingrui.studyagent", "java.util", "java.lang");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /** 死信按原 routing key（= 原队列名）路由回死信队列 */
    @Bean
    public Binding deadLetterBindingForIngest(
            @Qualifier("deadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                .with(props.getMq().getIngestQueue());
    }

    @Bean
    public Binding deadLetterBindingForReminder(
            @Qualifier("deadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                .with(props.getMq().getReminderQueue());
    }

    @Bean
    public Queue documentIngestQueue() {
        return QueueBuilder.durable(props.getMq().getIngestQueue())
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(props.getMq().getIngestQueue())
                .build();
    }

    @Bean
    public Queue reminderPushQueue() {
        return QueueBuilder.durable(props.getMq().getReminderQueue())
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(props.getMq().getReminderQueue())
                .build();
    }
}
