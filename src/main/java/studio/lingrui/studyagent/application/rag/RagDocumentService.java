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
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;
import studio.lingrui.studyagent.application.port.MqPort;
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
    private final KnowledgeKeywordSearchService keywordSearch;
    private final StudyAgentProperties props;
    private final ObjectProvider<MqPort> mqGatewayProvider;

    /**
     * 上传并启动摄入。
     *
     * <p><b>刻意不加 @Transactional</b>：摄入（Tika 解析 + 向量化）是秒级到分钟级的重活，
     * 且 MQ 启用时要把消息发到队列。如果包在事务里会有两个问题：
     * <ol>
     *   <li>消息在事务提交前就发出去了，消费者可能还读不到这条文档（publish-in-transaction 竞态）；</li>
     *   <li>{@link RagIngestService#ingest} 在失败时会写 FAILED 状态再抛异常，
     *       外层事务回滚会把这条 FAILED 记录一起抹掉——磁盘上留下孤儿文件，
     *       前端却看不到任何失败痕迹（"失败可见"的设计被事务吃掉）。</li>
     * </ol>
     * 这里只做一次单行插入（Repository 自带事务），随后在事务外派发。
     */
    public KnowledgeDocument upload(Long userId, MultipartFile file,
                                    DocumentSourceType sourceType, String tagsJson) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件为空");
        }
        long maxBytes = (long) props.getFile().getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "文件超过大小上限 " + props.getFile().getMaxFileSizeMb() + "MB");
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

    /**
     * 删除文档。
     *
     * <p>顺序刻意是「先删元数据行 → 再尽力清文件与向量」：如果先删文件而事务回滚，
     * 就会留下指向不存在文件的脏记录（重新索引必失败）。反过来只可能残留一点磁盘/内存垃圾，
     * 属于可接受的泄漏，不会破坏一致性。
     */
    public void delete(Long userId, Long docId) {
        KnowledgeDocument doc = documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
        documents.delete(doc);
        if (doc.getStoredPath() != null) {
            fileStore.delete(doc.getStoredPath());
        }
        vectorIndex.deleteDocument(docId, doc.getChunkCount() == null ? 0 : doc.getChunkCount());
    }

    /**
     * 重新摄入（对 FAILED/PENDING/INDEXED 均可）。
     *
     * <p>同样不加 @Transactional：摄入失败时要让 FAILED 状态与错误信息真正落库，
     * 否则前端只会看到"失败"却查不到原因。
     */
    public KnowledgeDocument reindex(Long userId, Long docId) {
        documents.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "文档不存在"));
        return ingestService.ingest(docId);
    }

    /**
     * 语义检索（供 API 直接查看知识库命中）：向量检索失败或无命中时降级为关键词检索。
     */
    public List<studio.lingrui.studyagent.domain.rag.RetrievedChunk> search(Long userId, String query,
                                                                            Integer topK) {
        if (query == null || query.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "检索词不能为空");
        }
        int k = topK == null ? props.getRag().getTopK() : Math.min(Math.max(topK, 1), 20);
        try {
            List<studio.lingrui.studyagent.domain.rag.RetrievedChunk> hits =
                    vectorIndex.search(query, k, props.getRag().getMinScore(), userId);
            if (!hits.isEmpty()) {
                return hits;
            }
            log.info("向量检索无命中，降级为关键词检索: {}", query);
        } catch (Exception e) {
            log.warn("向量检索失败，降级为关键词检索: {}", e.getMessage());
        }
        return keywordSearch.search(userId, query, k);
    }

    /**
     * 派发摄入任务：MQ 启用时入队异步处理，否则当前线程同步执行。
     *
     * <p>注意调用点必须在任何数据库写事务<b>之外</b>（见 {@link #upload}）：
     * 消息一旦先于事务提交发出，消费者就可能查不到这条文档。
     */
    private void dispatch(Long docId) {
        MqPort mq = mqGatewayProvider.getIfAvailable();
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
