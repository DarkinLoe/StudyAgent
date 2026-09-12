package studio.lingrui.studyagent.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求访问日志 + 链路追踪。
 *
 * <ul>
 *   <li>为每个请求生成/透传 {@code X-Trace-Id} 并写入 MDC，后续所有日志自动带上；</li>
 *   <li>响应头回写 traceId，便于前端/网关与后端日志对齐；</li>
 *   <li>记录方法、路径、状态码、耗时；查询串里的 token/password 等敏感参数会被打码；</li>
 *   <li>跳过 /actuator 探针请求，避免健康检查刷屏。</li>
 * </ul>
 *
 * <p>顺序设为最高优先级，保证它在 Spring Security 过滤链之前执行，从而让认证相关日志也带上 traceId。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_TRACE, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            String path = request.getRequestURI();
            if (!path.startsWith("/actuator")) {
                log.info("{} {}{} -> {} ({}ms)", request.getMethod(), path,
                        mask(request.getQueryString()), response.getStatus(), costMs);
            }
            MDC.remove(MDC_TRACE);
        }
    }

    /** 对查询串中的敏感字段打码，避免密钥/令牌进日志 */
    private String mask(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        String masked = queryString.replaceAll(
                "(?i)(token|password|passwd|pwd|api[-_]?key|secret|authorization)=([^&]*)", "$1=***");
        return "?" + masked;
    }
}
