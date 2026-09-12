package studio.lingrui.studyagent.application.port;

import java.io.InputStream;

/**
 * 文档文本抽取端口：应用层只声明"把字节流抽成纯文本"，不关心用的是 Tika 还是别的解析器。
 */
public interface TextExtractionPort {

    /**
     * 抽取文本。
     *
     * @param in 文档字节流（由调用方负责关闭）
     * @return 解析出的纯文本，保证非空白
     */
    String extract(InputStream in);
}
