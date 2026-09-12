package studio.lingrui.studyagent.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 编程式事务模板。
 *
 * <p>用途：像「调一次大模型」这种秒级到分钟级的慢操作，必须把前后的数据库写操作
 * 切成两段独立短事务（{@code TransactionTemplate} 显式圈边界），
 * 而不是整段套一个 {@code @Transactional}——否则一次对话就会独占一条数据库连接
 * 直到模型返回，连接池很快被打满，慢接口会拖垮所有接口。
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
