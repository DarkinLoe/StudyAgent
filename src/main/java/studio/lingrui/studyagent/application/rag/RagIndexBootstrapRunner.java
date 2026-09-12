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

/**
 * 启动时重建向量索引：进程内向量库（SimpleVectorStore）重启即丢失，
 * 这里用文档已保存的解析文本重新向量化，保证重启后 RAG 仍可用。
 *
 * <p>可通过 study-agent.rag.reindex-on-startup=false 关闭（文档多或需要省 token 时）。
 * 单篇失败只告警，不影响应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexBootstrapRunner implements ApplicationRunner {

    private final KnowledgeDocumentRepository documents;
    private final RagIngestService ingestService;
    private final StudyAgentProperties props;

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
        log.info("启动向量库重建完成：成功 {} 篇，失败 {} 篇", success, failed);
    }
}
