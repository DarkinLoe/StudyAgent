package studio.lingrui.studyagent.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.io.IOException;
import java.time.Duration;

/**
 * 接口限流（Redis 计数，固定窗口：按分钟）。
 *
 * <ul>
 *   <li>对话接口按「用户」限流——LLM 调用昂贵，防止单用户刷爆成本；</li>
 *   <li>上传接口按「用户」限流——解析/向量化是重活；</li>
 *   <li>登录接口按「客户端 IP」限流——防暴力破解。</li>
 * </ul>
 *
 * <p>设计取舍：Redis 故障时**放行**（限流是保护措施，不应成为可用性单点）；
 * 计数器 key 自带分钟时间戳，TTL 2 分钟自动清理，无需额外清扫任务。
 * 该过滤器在 JWT 认证之后执行，因此能拿到当前用户 id。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final StudyAgentProperties props;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Rule rule = ruleOf(request);
        if (!props.getRateLimit().isEnabled() || rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = rule.perUser()
                ? String.valueOf(UserContext.peekUserId() == null ? "anonymous" : UserContext.peekUserId())
                : clientIp(request);
        String key = "rate:" + rule.name() + ":" + subject + ":" + (System.currentTimeMillis() / 60_000);

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(2));
            }
            if (count != null && count > rule.limit()) {
                log.warn("触发限流 {} limit={}/min count={}", key, rule.limit(), count);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                        ApiResponse.fail(ErrorCode.TOO_MANY_REQUESTS.getCode(),
                                ErrorCode.TOO_MANY_REQUESTS.getDefaultMessage())));
                return;
            }
        } catch (Exception e) {
            log.warn("限流检查失败（放行）: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private Rule ruleOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        var cfg = props.getRateLimit();
        if (path.startsWith("/api/agent/chat")) {
            return new Rule("chat", cfg.getChatPerMinute(), true);
        }
        if (path.startsWith("/api/rag/documents")) {
            return new Rule("upload", cfg.getUploadPerMinute(), true);
        }
        if (path.startsWith("/api/auth/login")) {
            return new Rule("login", cfg.getLoginPerMinute(), false);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Rule(String name, int limit, boolean perUser) {
    }
}
