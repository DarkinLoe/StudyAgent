package studio.lingrui.studyagent.interfaces.rest.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import studio.lingrui.studyagent.application.rag.RagDocumentService;
import studio.lingrui.studyagent.domain.rag.DocumentSourceType;
import studio.lingrui.studyagent.domain.rag.KnowledgeDocument;
import studio.lingrui.studyagent.domain.rag.RetrievedChunk;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.api.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人 RAG 知识库：上传课程表/学习安排/PPT/题目资料等，自动解析索引。
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagDocumentController {

    private final RagDocumentService ragDocumentService;

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ApiResponse<DocumentView> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "sourceType", defaultValue = "OTHER") DocumentSourceType sourceType,
            @RequestParam(value = "tags", required = false) String tags) {
        KnowledgeDocument doc = ragDocumentService.upload(
                UserContext.getUserId(), file, sourceType, tags);
        return ApiResponse.ok(DocumentView.from(doc));
    }

    @GetMapping("/documents")
    public ApiResponse<PageResult<DocumentView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<KnowledgeDocument> docs = ragDocumentService.list(UserContext.getUserId(), page, size);
        return ApiResponse.ok(PageResult.of(docs, DocumentView::from));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentView> detail(@PathVariable Long id) {
        return ApiResponse.ok(DocumentView.from(ragDocumentService.detail(UserContext.getUserId(), id)));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ragDocumentService.delete(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/documents/{id}/reindex")
    public ApiResponse<DocumentView> reindex(@PathVariable Long id) {
        return ApiResponse.ok(DocumentView.from(
                ragDocumentService.reindex(UserContext.getUserId(), id)));
    }

    @GetMapping("/search")
    public ApiResponse<List<RetrievedChunk>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", required = false) Integer topK) {
        return ApiResponse.ok(ragDocumentService.search(UserContext.getUserId(), query, topK));
    }

    /**
     * 文档视图（列表/详情，隐藏大文本）。
     */
    public record DocumentView(Long id, String name, String sourceType, String status,
                               String contentType, Long sizeBytes, Integer chunkCount,
                               String errorMsg, String tagsJson,
                               LocalDateTime createdAt, LocalDateTime updatedAt,
                               LocalDateTime indexedAt) {
        static DocumentView from(KnowledgeDocument d) {
            return new DocumentView(d.getId(), d.getName(),
                    d.getSourceType() == null ? null : d.getSourceType().name(),
                    d.getStatus() == null ? null : d.getStatus().name(),
                    d.getContentType(), d.getSizeBytes(), d.getChunkCount(),
                    d.getErrorMsg(), d.getTagsJson(),
                    d.getCreatedAt(), d.getUpdatedAt(), d.getIndexedAt());
        }
    }
}
