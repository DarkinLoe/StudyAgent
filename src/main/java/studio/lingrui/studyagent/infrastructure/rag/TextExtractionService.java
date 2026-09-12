package studio.lingrui.studyagent.infrastructure.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;
import studio.lingrui.studyagent.application.port.TextExtractionPort;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.io.InputStream;

/**
 * 文档文本抽取适配器：基于 Apache Tika 实现 {@link TextExtractionPort}，
 * 支持 PDF/PPT/PPTX/Word/Excel/TXT/Markdown 等。
 *
 * <p><b>面向不可信输入的三道闸</b>（用户上传的文件必须当作恶意输入对待）：
 * <ol>
 *   <li><b>输出字符数上限</b>：{@value #MAX_TEXT_CHARS} 字符后停止累积。
 *       原实现用 {@code BodyContentHandler(-1)}（不限长），一个小体积的压缩文档
 *       （或解压炸弹）就能膨胀出 GB 级文本把堆打爆——这是 Tika 类服务最常见的 OOM 来源；</li>
 *   <li><b>关闭 PDF 内联图片抽取</b>：PDF 里成百上千个内联图片是另一个经典内存炸弹，
 *       本项目只关心文字，显式关掉；</li>
 *   <li><b>文件体积上限</b>在 {@code RagDocumentService.upload} 处按配置拦截
 *       （Servlet multipart 限制之外再兜一层，MQ 异步摄入同样受保护）。</li>
 * </ol>
 *
 * <p>仍未覆盖（见 README 已知边界）：没有做真实文件类型（magic number）校验、
 * 没有杀毒扫描、没有解析超时与进程级沙箱。生产环境建议把解析放进受限子进程或
 * 独立服务，并接入病毒扫描。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextExtractionService implements TextExtractionPort {

    /** 单篇文档最多抽取的字符数（约 2M 字符）：超过则截断，避免堆被撑爆 */
    static final int MAX_TEXT_CHARS = 2_000_000;

    @Override
    public String extract(InputStream in) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARS);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setExtractInlineImages(false);
            context.set(PDFParserConfig.class, pdfConfig);

            try {
                parser.parse(in, handler, metadata, context);
            } catch (SAXException e) {
                // 到达写入上限时 Tika 抛 WriteLimitReachedException；此时已解析部分仍然可用，
                // 取截断结果即可（真正的内容错误继续向上抛，文档会被标记 FAILED）
                if (!WriteLimitReachedException.isWriteLimitReached(e)) {
                    throw e;
                }
                log.warn("文档解析达到 {} 字符上限，已截断后续内容", MAX_TEXT_CHARS);
            }

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
