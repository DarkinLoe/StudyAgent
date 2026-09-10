package studio.lingrui.studyagent.application.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.application.port.FileStorePort;
import studio.lingrui.studyagent.application.port.VectorIndexPort;
import studio.lingrui.studyagent.domain.rag.IndexStatus;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;
import studio.lingrui.studyagent.infrastructure.rag.TextExtractionService;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.io.InputStream;
import java.util.List;

/**
 * RAG 文档摄入（应用服务）：
 * 解析文本 → 分块向量化 → 更新文档状态。同步执行；异步入口见 MQ 消费者。
 * 说明：不包大事务，异常时单独落 FAILED 状态并向上抛，保证失败可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestService {

    private final KnowledgeDocumentRepository documents;
    private final FileStorePort fileStore;
    private final TextExtractionService textExtraction;
    private final VectorIndexPort vectorIndex;
    private final StudyAgentProperties props;

    /**
     * 摄入/重建单篇文档（重新解析文件）。失败时置 FAILED 并抛出 BizException。
     */
    public KnowledgeDocument ingest(Long docId) {
        KnowledgeDocument doc = documents.findById(docId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在: " + docId));

        doc.markProcessing();
        documents.save(doc);

        try {
            String text;
            try (InputStream in = fileStore.open(doc.getStoredPath())) {
                text = textExtraction.extract(in);
            }
            int chunkCount = indexText(doc, text);
            doc.markIndexed(text, chunkCount);
            return documents.save(doc);
        } catch (BizException e) {
            doc.markFailed(e.getMessage());
            documents.save(doc);
            throw e;
        } catch (Exception e) {
            log.error("文档摄入失败 docId={}", docId, e);
            doc.markFailed("摄入失败: " + e.getMessage());
            documents.save(doc);
            throw new BizException(ErrorCode.DOC_PARSE_FAILED, "文档摄入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 仅用已保存的解析文本重建向量索引（不重新解析文件）。
     * 用于应用重启后恢复进程内向量库，避免"重启即检索不到"。
     */
    public KnowledgeDocument rebuildFromStoredText(KnowledgeDocument doc) {
        if (doc.getTextContent() == null || doc.getTextContent().isBlank()) {
            throw new BizException(ErrorCode.DOC_NOT_INDEXED,
                    "文档缺少已解析文本，无法重建索引: " + doc.getId());
        }
        int chunkCount = indexText(doc, doc.getTextContent());
        doc.markIndexed(doc.getTextContent(), chunkCount);
        return documents.save(doc);
    }

    /**
     * 校验来源类型/扩展名等由上传侧负责；此处仅按 docId 取回文档供展示状态机使用。
     */
    public KnowledgeDocument getById(Long docId, Long userId) {
        return documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
    }

    public List<KnowledgeDocument> listIndexed(Long userId) {
        return documents.findByUserIdAndStatus(userId, IndexStatus.INDEXED);
    }

    private int indexText(KnowledgeDocument doc, String text) {
        int oldChunkCount = doc.getChunkCount() == null ? 0 : doc.getChunkCount();
        return vectorIndex.indexDocument(
                doc.getId(),
                doc.getName(),
                doc.getSourceType(),
                text,
                props.getRag().getChunkSize(),
                props.getRag().getChunkOverlap(),
                oldChunkCount,
                doc.getUserId());
    }
}
