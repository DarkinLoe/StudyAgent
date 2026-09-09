package studio.lingrui.studyagent.application.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import studio.lingrui.studyagent.application.port.FileStorePort;
import studio.lingrui.studyagent.application.port.VectorIndexPort;
import studio.lingrui.studyagent.domain.rag.DocumentSourceType;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;
import studio.lingrui.studyagent.infrastructure.mq.MqGateway;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.util.List;
import java.util.Locale;

/**
 * 个人知识库文档管理（上传/列表/删除）＋ 摄入任务派发。
 * MQ 启用时发布到摄入队列，由消费者异步处理；否则同步摄入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagDocumentService {

    private final KnowledgeDocumentRepository documents;
    private final FileStorePort fileStore;
    private final VectorIndexPort vectorIndex;
    private final RagIngestService ingestService;
    private final StudyAgentProperties props;
    private final ObjectProvider<MqGateway> mqGatewayProvider;

    /**
     * 上传并启动摄入。
     */
    @Transactional
    public KnowledgeDocument upload(Long userId, MultipartFile file,
                                    DocumentSourceType sourceType, String tagsJson) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件为空");
        }
        String name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = extOf(name);
        if (ext.isEmpty() || !props.getFile().getAllowedExtensions().contains(ext)) {
            throw new BizException(ErrorCode.DOC_UNSUPPORTED_TYPE,
                    "不支持的文件类型: " + (ext.isEmpty() ? "(无扩展名)" : ext)
                            + "，允许: " + props.getFile().getAllowedExtensions());
        }
        String storedPath = fileStore.store(file, "docs");
        KnowledgeDocument doc = KnowledgeDocument.pending(userId, name, storedPath,
                file.getContentType(), file.getSize(), sourceType, tagsJson);
        doc = documents.save(doc);
        dispatch(doc.getId());
        return doc;
    }

    public Page<KnowledgeDocument> list(Long userId, int page, int size) {
        return documents.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument detail(Long userId, Long docId) {
        return documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
    }

    @Transactional
    public void delete(Long userId, Long docId) {
        KnowledgeDocument doc = documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
        if (doc.getStoredPath() != null) {
            fileStore.delete(doc.getStoredPath());
        }
        vectorIndex.deleteDocument(docId, doc.getChunkCount() == null ? 0 : doc.getChunkCount());
        documents.delete(doc);
    }

    /**
     * 重新摄入（对 FAILED/PENDING/INDEXED 均可）。
     */
    @Transactional
    public KnowledgeDocument reindex(Long userId, Long docId) {
        documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
        return ingestService.ingest(docId);
    }

    /**
     * 语义检索（供 API 直接查看知识库命中）。
     */
    public List<studio.lingrui.studyagent.domain.rag.RetrievedChunk> search(Long userId, String query,
                                                                            Integer topK) {
        if (query == null || query.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "检索词不能为空");
        }
        int k = topK == null ? props.getRag().getTopK() : Math.min(Math.max(topK, 1), 20);
        return vectorIndex.search(query, k, props.getRag().getMinScore());
    }

    private void dispatch(Long docId) {
        MqGateway mq = mqGatewayProvider.getIfAvailable();
        if (props.getMq().isEnabled() && mq != null) {
            log.info("发布文档摄入任务 docId={} 到队列 {}", docId, props.getMq().getIngestQueue());
            mq.publishDocumentIngest(docId);
        } else {
            ingestService.ingest(docId);
        }
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
