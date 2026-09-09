package studio.lingrui.studyagent.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;

/**
 * MVC 扩展：注册用户上下文拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StudyAgentProperties props;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserIdInterceptor(props.getUser().getDefaultUserId()))
                .addPathPatterns("/api/**");
    }
}
