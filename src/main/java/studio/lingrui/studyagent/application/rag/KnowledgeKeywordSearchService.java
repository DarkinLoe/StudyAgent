package studio.lingrui.studyagent.application.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeKeywordSearchService {

    private static final int WINDOW = 200;

    private final KnowledgeDocumentRepository documents;

    public List<RetrievedChunk> search(Long userId, String query, int limit) {
        List<String> tokens = KeywordSnippetExtractor.tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDocument> docs = documents.findByUserIdAndStatus(userId, IndexStatus.INDEXED);
        List<RetrievedChunk> hits = new ArrayList<>();
        for (KnowledgeDocument doc : docs) {
            String text = doc.getTextContent();
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
        log.info("关键词降级检索完成 userId={} query={} 命中={}", userId, query, result.size());
        return result;
    }
}
