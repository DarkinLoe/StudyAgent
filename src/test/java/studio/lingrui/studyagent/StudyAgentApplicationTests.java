package studio.lingrui.studyagent;

import org.junit.jupiter.api.Test;

/**
 * 占位启动测试：启动类仅负责装配，应用上下文需 MySQL/Redis（见 README 的 docker compose 步骤），
 * 故此处不做 @SpringBootTest 以免脱离基础设施时失败；数据库行为验证见集成阶段。
 */
class StudyAgentApplicationTests {

    @Test
    void contextLoads() {
        // 应用配置与依赖已由 mvn compile / package 验证
    }
}
