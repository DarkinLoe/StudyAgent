package studio.lingrui.studyagent.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2 的 ObjectMapper（显式声明）。
 *
 * <p>背景：Spring Boot 4 的 {@code spring-boot-jackson} 自动配置提供的是 <b>Jackson 3</b>
 * （{@code tools.jackson.databind.ObjectMapper}）；而本项目代码与 Spring AI 生态仍使用
 * <b>Jackson 2</b>（{@code com.fasterxml.jackson}），因此容器中没有 Jackson 2 的 ObjectMapper，
 * 直接注入会启动失败（实战踩坑记录）。
 *
 * <p>权衡：迁移到 Jackson 3 是正解，但涉及多文件 API 变更（JsonNode/TypeReference/异常类型）；
 * 面试交付期先用"显式 Bean"过渡，后续统一迁移后本类可删除。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
