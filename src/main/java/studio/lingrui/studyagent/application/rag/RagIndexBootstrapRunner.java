package studio.lingrui.studyagent.application.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.domain.rag.IndexStatus;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 启动时重建向量索引：进程内向量库（SimpleVectorStore）重启即丢失，
 * 这里用文档已保存的解析文本重新向量化，保证重启后 RAG 仍可用。
 *
 * <p><b>为什么放到后台线程</b>：重建要对每篇已索引文档调一次 Embedding。
 * 原先在 {@link ApplicationRunner} 里同步跑，意味着启动耗时与文档数成正比，
 * 且在这期间 {@code /actuator/health} 一直不 ready（容器编排下探针会判定启动失败并重启容器）。
 * 现在启动立即返回、重建在后台单线程串行进行；代价是重建完成前 RAG 检索结果可能不完整。
 *
 * <p>可通过 {@code study-agent.rag.reindex-on-startup=false} 关闭（文档多或需要省 token 时）。
 * 单篇失败只告警，不影响应用启动与其它文档的重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexBootstrapRunner implements ApplicationRunner {

    private final KnowledgeDocumentRepository documents;
    private final RagIngestService ingestService;
    private final StudyAgentProperties props;
    private final ExecutorService ragBootstrapExecutor;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getRag().isReindexOnStartup()) {
            log.info("启动重建向量索引已关闭（study-agent.rag.reindex-on-startup=false）");
            return;
        }
        List<KnowledgeDocument> indexed = documents.findByStatus(IndexStatus.INDEXED);
        if (indexed.isEmpty()) {
            log.info("无已索引文档，跳过向量库重建");
            return;
        }
        log.info("向量库重建已在后台开始（{} 篇），期间检索结果可能不完整", indexed.size());
        CompletableFuture.runAsync(() -> rebuild(indexed), ragBootstrapExecutor)
                .exceptionally(e -> {
                    log.error("启动向量库重建任务异常终止", e);
                    return null;
                });
    }

    private void rebuild(List<KnowledgeDocument> indexed) {
        long start = System.currentTimeMillis();
        int success = 0;
        int failed = 0;
        for (KnowledgeDocument doc : indexed) {
            try {
                ingestService.rebuildFromStoredText(doc);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("重建向量索引失败 docId={}：{}", doc.getId(), e.getMessage());
            }
        }
        log.info("启动向量库重建完成：成功 {} 篇，失败 {} 篇，耗时 {} ms",
                success, failed, System.currentTimeMillis() - start);
    }
}
