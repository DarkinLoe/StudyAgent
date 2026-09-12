package studio.lingrui.studyagent.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动期后台任务线程池。
 *
 * <p>目前只服务一件事：应用启动后用已保存文本重建进程内向量索引。
 * 单线程是刻意的——重建会对每篇文档调一次 Embedding，串行执行既省额度也更友好；
 * 守护线程 + {@code destroyMethod="shutdown"} 保证它不会拖住进程退出
 * （重建是可重入的，下次启动会重新来一遍）。
 */
@Configuration
public class RagBootstrapExecutorConfig {

    public static final String BEAN_NAME = "ragBootstrapExecutor";

    @Bean(name = BEAN_NAME, destroyMethod = "shutdown")
    public ExecutorService ragBootstrapExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rag-index-bootstrap");
            thread.setDaemon(true);
            return thread;
        });
    }
}
