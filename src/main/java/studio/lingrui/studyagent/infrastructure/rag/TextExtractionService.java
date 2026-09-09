package studio.lingrui.studyagent.infrastructure.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.io.InputStream;

/**
 * 文档文本抽取：基于 Apache Tika，支持 PDF/PPT/PPTX/Word/Excel/TXT/Markdown 等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextExtractionService {

    public String extract(InputStream in) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1); // 不限长度
            Metadata metadata = new Metadata();
            parser.parse(in, handler, metadata);
            String text = handler.toString();
            if (text == null || text.isBlank()) {
                throw new BizException(ErrorCode.DOC_PARSE_FAILED, "未能从文档中解析出文本内容");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档解析失败", e);
            throw new BizException(ErrorCode.DOC_PARSE_FAILED, "文档解析失败: " + e.getMessage());
        }
    }
}
