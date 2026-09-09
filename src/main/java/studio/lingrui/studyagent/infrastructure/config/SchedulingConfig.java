package studio.lingrui.studyagent.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务（学习提示轮询等）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
