package studio.lingrui.studyagent.infrastructure.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import studio.lingrui.studyagent.application.port.FileStorePort;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地磁盘文件存储（可替换为对象存储实现）。
 */
@Slf4j
@Service
public class LocalFileStoreAdapter implements FileStorePort {

    private final Path root;

    public LocalFileStoreAdapter(StudyAgentProperties props) {
        this.root = Paths.get(props.getFile().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    @Override
    public String store(MultipartFile file, String subDir) {
        String ext = extension(file.getOriginalFilename());
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = resolveDir(subDir);
        Path target = dir.resolve(storedName);
        try {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return root.relativize(target).toString().replace('\\', '/');
        } catch (IOException e) {
            log.error("保存文件失败", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream open(String storedPath) {
        Path file = resolve(storedPath);
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在: " + storedPath);
        }
    }

    @Override
    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", storedPath, e);
        }
    }

    private Path resolveDir(String subDir) {
        Path dir = root;
        if (subDir != null && !subDir.isBlank()) {
            dir = root.resolve(subDir).normalize();
            if (!dir.startsWith(root)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "非法子目录");
            }
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "目录创建失败: " + dir);
        }
        return dir;
    }

    private Path resolve(String storedPath) {
        Path p = root.resolve(storedPath).normalize();
        if (!p.startsWith(root)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法文件路径");
        }
        return p;
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }
}
