package studio.lingrui.studyagent.application.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.domain.rag.IndexStatus;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocumentRepository;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 关键词降级检索：当 Embedding/向量检索不可用（供应商无 embedding、额度不足、网络故障）时，
 * 直接在已索引文档的解析文本中做关键词匹配。
 *
 * <p>score 语义与向量相似度不同：这里是"命中关键词占比"（0~1），仅用于排序，不可与向量分直接比较。
 *
 * <p><b>为什么要封顶</b>：这条路径是"把正文读进 JVM 再逐 token 扫字符串"，
 * 成本是 O(文档数 × token 数 × 文本长度)。如果对全部文档做，用户文档一多就是
 * 一次请求打爆内存/CPU 的性能悬崖——而它本身只是降级路径，应当"兜底可用"而不是"全量精确"。
 * 因此：SQL 侧按 id 倒序分页取 {@value #MAX_DOCS_SCANNED} 篇，正文再截断到
 * {@value #MAX_CHARS_PER_DOC} 字符。
 *
 * <p>根治方案是把关键词检索换成倒排索引（MySQL FULLTEXT / ES / 向量库自带 filter），
 * 见 README「后续演进」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeKeywordSearchService {

    private static final int WINDOW = 200;
    /** 单次降级检索最多扫描的文档数（按上传时间倒序，新资料优先） */
    static final int MAX_DOCS_SCANNED = 200;
    /** 单篇文档最多参与匹配的字符数 */
    static final int MAX_CHARS_PER_DOC = 100_000;

    private final KnowledgeDocumentRepository documents;

    public List<RetrievedChunk> search(Long userId, String query, int limit) {
        List<String> tokens = KeywordSnippetExtractor.tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDocument> docs = documents.findByUserIdAndStatusOrderByIdDesc(
                userId, IndexStatus.INDEXED, PageRequest.of(0, MAX_DOCS_SCANNED));
        if (docs.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> hits = new ArrayList<>();
        for (KnowledgeDocument doc : docs) {
            String text = clip(doc.getTextContent());
            if (text == null || text.isBlank()) {
                continue;
            }
            int matched = 0;
            String snippet = null;
            for (String token : tokens) {
                String hit = KeywordSnippetExtractor.extract(text, token, WINDOW);
                if (hit != null) {
                    matched++;
                    if (snippet == null) {
                        snippet = hit;
                    }
                }
            }
            if (matched > 0) {
                hits.add(new RetrievedChunk(doc.getId(), doc.getName(), doc.getSourceType(),
                        (double) matched / tokens.size(), snippet));
            }
        }
        hits.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        List<RetrievedChunk> result = hits.stream().limit(Math.max(limit, 1)).toList();
        log.info("关键词降级检索完成 userId={} query={} 扫描文档={} 命中={}",
                userId, query, docs.size(), result.size());
        return result;
    }

    private String clip(String text) {
        if (text == null || text.length() <= MAX_CHARS_PER_DOC) {
            return text;
        }
        return text.substring(0, MAX_CHARS_PER_DOC);
    }
}
