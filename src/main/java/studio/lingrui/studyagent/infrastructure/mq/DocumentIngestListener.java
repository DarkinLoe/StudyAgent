package studio.lingrui.studyagent.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.application.rag.RagIngestService;
import studio.lingrui.studyagent.infrastructure.mq.message.DocumentIngestMessage;

/**
 * 文档摄入任务消费者（study-agent.mq.enabled=true 时注册）。
 *
 * <p>失败处理：<b>不吞异常</b>。以前这里 catch 掉异常后正常返回，等于把任务静默 ACK 丢弃。
 * 现在让异常抛出，由 listener 的重试（见 application.yml 的
 * {@code spring.rabbitmq.listener.simple.retry}）重投 3 次，仍失败则经死信交换机进入死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class DocumentIngestListener {

    private final RagIngestService ingestService;

    @RabbitListener(queues = "${study-agent.mq.ingest-queue}")
    public void onIngest(DocumentIngestMessage message) {
        if (message == null || message.docId() == null) {
            // 结构不对的消息重试多少次都没用：记错误日志后直接确认，避免占住队列
            log.error("文档摄入消息缺少 docId，已丢弃: {}", message);
            return;
        }
        log.info("消费文档摄入任务 docId={}", message.docId());
        ingestService.ingest(message.docId());
    }
}
