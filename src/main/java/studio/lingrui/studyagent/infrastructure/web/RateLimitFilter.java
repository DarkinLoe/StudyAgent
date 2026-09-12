package studio.lingrui.studyagent.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.lingrui.studyagent.shared.api.ApiResponse;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 接口限流（Redis ZSet + Lua 原子滑动窗口，窗口 1 分钟）。
 *
 * <ul>
 *   <li>对话接口按「用户」限流——LLM 调用昂贵，防止单用户刷爆成本；</li>
 *   <li>上传接口按「用户」限流——解析/向量化是重活；</li>
 *   <li>登录接口按「客户端 IP」限流——防暴力破解。</li>
 * </ul>
 *
 * <p>相对早期实现的三个修正：
 * <ol>
 *   <li><b>固定窗口 → 滑动窗口</b>：固定窗口在跨分钟边界时允许 2× 突发；</li>
 *   <li><b>计数与写入原子化</b>：原来"先 INCR 再 EXPIRE"是两条命令，一旦 EXPIRE 那步失败
 *       （或进程在两步之间挂掉），key 会没有 TTL 而永久存在——该用户被永久限流。
 *       现在"清理过期、统计、写入"全部收敛进一段 Lua，在 Redis 单线程内原子执行；</li>
 *   <li><b>客户端 IP 取最后一段</b>：X-Forwarded-For 由各级代理逐段追加，
 *       只有最后一段来自我们信任的反向代理，取第一段等于让攻击者伪造 header 绕过登录限流。</li>
 * </ol>
 *
 * <p>设计取舍：Redis 故障时<b>放行</b>（限流是保护措施，不应成为可用性单点）。
 * 该过滤器在 JWT 认证之后执行，因此能拿到当前用户 id。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * 滑动窗口限流脚本，单次原子执行：
     * ① 移除窗口外的旧记录；② 统计窗口内请求数；③ 未超限才记录本次请求。
     *
     * @return 允许时返回"包含本次在内的窗口请求数"；拒绝时返回 limit+1（恒大于 limit）
     */
    private static final RedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return limit + 1
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return count + 1
            """, Long.class);

    private static final long WINDOW_MS = 60_000L;

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
                : resolveClientIp(request);
        String key = "rate:" + rule.name() + ":" + subject;

        try {
            Long result = redis.execute(SLIDING_WINDOW_SCRIPT, List.of(key),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(rule.limit()),
                    System.currentTimeMillis() + "-" + UUID.randomUUID());
            if (isOverLimit(result, rule.limit())) {
                log.warn("触发限流 key={} limit={}/min result={}", key, rule.limit(), result);
                writeTooManyRequests(response);
                return;
            }
        } catch (Exception e) {
            // Redis 不可用：放行，避免限流组件变成可用性单点
            log.warn("限流检查失败（放行）: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    /** 脚本返回值是否表示超限（拒绝时脚本返回 limit+1） */
    static boolean isOverLimit(Long scriptResult, int limit) {
        return scriptResult != null && scriptResult > limit;
    }

    /**
     * 解析真实客户端 IP。
     *
     * <p>X-Forwarded-For 形如 {@code 伪造值, ... , 真实客户端}：每一跳代理在末尾追加自己看到的来源，
     * 因此最右边的非空项才是可信的（由我们自己的 Nginx 用 {@code $proxy_add_x_forwarded_for} 写入）。
     * 取最左边一项会让攻击者用自定义 header 绕过按 IP 的登录限流。
     */
    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(WINDOW_MS / 1000));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.TOO_MANY_REQUESTS.getCode(),
                        ErrorCode.TOO_MANY_REQUESTS.getDefaultMessage())));
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

    private record Rule(String name, int limit, boolean perUser) {
    }
}
