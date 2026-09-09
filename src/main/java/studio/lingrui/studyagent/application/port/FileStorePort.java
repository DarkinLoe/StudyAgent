package studio.lingrui.studyagent.application.port;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储端口（本地磁盘实现，可替换为 OSS 等对象存储）。
 */
public interface FileStorePort {

    /**
     * 保存上传文件，返回存储相对路径。
     */
    String store(MultipartFile file, String subDir);

    /**
     * 打开已存储文件流。
     */
    InputStream open(String storedPath);

    /**
     * 删除已存储文件。
     */
    void delete(String storedPath);
}
