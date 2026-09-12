package studio.lingrui.studyagent.shared.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 业务自定义配置项，前缀 study-agent.*（见 application.yml）。
 */
@Data
@ConfigurationProperties(prefix = "study-agent")
public class StudyAgentProperties {

    private User user = new User();
    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();
    private FileProp file = new FileProp();
    private Rag rag = new Rag();
    private Mq mq = new Mq();
    private Reminder reminder = new Reminder();
    private Cache cache = new Cache();
    private Flyway flyway = new Flyway();

    @Data
    public static class User {
        /** 登录体系上线前的默认用户 id */
        private Long defaultUserId = 1L;
    }

    @Data
    public static class Security {
        /** JWT 签名密钥（HS256，要求 >=32 字节）；为空则启动时生成临时随机密钥 */
        private String jwtSecret = "";
        /** token 有效期（分钟），默认 12 小时 */
        private long tokenTtlMinutes = 720;
        /** 定时任务是否使用 Redis 分布式锁（多实例部署时防重复执行） */
        private boolean distributedJobLock = true;
    }

    @Data
    public static class RateLimit {
        /** 是否开启限流 */
        private boolean enabled = true;
        /** 对话接口：每用户每分钟最多请求数 */
        private int chatPerMinute = 20;
        /** 上传接口：每用户每分钟最多请求数 */
        private int uploadPerMinute = 10;
        /** 登录接口：每 IP 每分钟最多尝试数（防爆破） */
        private int loginPerMinute = 10;
    }

    @Data
    public static class FileProp {
        /** 上传文件落盘目录 */
        private String uploadDir = "./data/uploads";
        /** 允许上传的扩展名（小写，不含点） */
        private List<String> allowedExtensions = List.of(
                "ppt", "pptx", "doc", "docx", "pdf", "txt", "md", "xlsx", "xls", "csv");
    }

    @Data
    public static class Rag {
        /** 文本分块大小（字符） */
        private int chunkSize = 800;
        /** 分块重叠（字符） */
        private int chunkOverlap = 100;
        /** 检索返回条数 */
        private int topK = 4;
        /** 相似度最低阈值（0~1，0 表示不过滤） */
        private double minScore = 0.0;
        /** 应用启动时是否用已保存文本重建进程内向量索引 */
        private boolean reindexOnStartup = true;
    }

    @Data
    public static class Mq {
        /** 是否启用 RabbitMQ（文档摄入/提醒消息异步化）；false 时同步回退执行 */
        private boolean enabled = false;
        /** 文档摄入任务队列 */
        private String ingestQueue = "study-agent.document.ingest";
        /** 学习提醒推送队列 */
        private String reminderQueue = "study-agent.reminder.push";
    }

    @Data
    public static class Reminder {
        /** 学习提示轮询 cron，默认每分钟 */
        private String pollCron = "0 * * * * *";
    }

    @Data
    public static class Cache {
        /** 默认缓存 TTL */
        private Duration defaultTtl = Duration.ofMinutes(10);
    }

    @Data
    public static class Flyway {
        /**
         * 启动时先 repair 再 migrate。
         * repair 会清理 flyway_schema_history 里的失败迁移记录并对齐校验和，
         * 使"曾经跑失败/脚本被修正过"的迁移能重新收敛；健康库上是 no-op。
         */
        private boolean repairOnStartup = true;
    }
}
