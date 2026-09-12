package studio.lingrui.studyagent.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;

/**
 * Flyway 迁移策略：<b>先 repair，再 migrate</b>。
 *
 * <p>为什么需要它：MySQL 的 DDL 不走事务，一个多语句迁移失败后会留下
 * "flyway_schema_history 里一行 failed 记录 + 数据库里一堆半成品"的状态，
 * 此后每次启动 Flyway 都会直接报错拒绝启动（validate 失败），人只能手动进库删记录。
 *
 * <p>{@code repair()} 负责把失败记录删掉、把已应用迁移的校验和对齐到当前脚本内容，
 * 之后 {@code migrate()} 就能把修正后的脚本重新按幂等方式跑一遍并收敛。
 * 健康库上 repair 是 no-op，因此默认开启；如需在严格环境关掉，
 * 设置 {@code study-agent.flyway.repair-on-startup=false}。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FlywayConfig {

    private final StudyAgentProperties props;

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            if (props.getFlyway().isRepairOnStartup()) {
                log.info("Flyway repair：清理失败迁移记录并对齐校验和（自愈历史破损迁移）");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
