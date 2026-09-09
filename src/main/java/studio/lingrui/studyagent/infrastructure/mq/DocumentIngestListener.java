package studio.lingrui.studyagent.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.application.rag.RagIngestService;

/**
 * 文档摄入任务消费者（study-agent.mq.enabled=true 时注册）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "study-agent.mq", name = "enabled", havingValue = "true")
public class DocumentIngestListener {

    private final RagIngestService ingestService;

    @RabbitListener(queues = "${study-agent.mq.ingest-queue}")
    public void onIngest(Long docId) {
        log.info("消费文档摄入任务 docId={}", docId);
        try {
            ingestService.ingest(docId);
        } catch (Exception e) {
            log.error("文档摄入任务执行失败 docId={}", docId, e);
        }
    }
}
