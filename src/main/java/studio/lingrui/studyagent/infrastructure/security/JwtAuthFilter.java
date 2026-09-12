package studio.lingrui.studyagent.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import studio.lingrui.studyagent.application.port.TokenPort;
import studio.lingrui.studyagent.infrastructure.web.UserContext;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 {@code Authorization: Bearer <token>}，
 * 校验通过后写入 Spring Security 上下文与 {@link UserContext}（业务代码继续用 UserContext.getUserId()）。
 *
 * <p>除签名与有效期外，还要检查令牌是否已被登出吊销（jti 黑名单）——
 * 无状态 JWT 自身无法撤回，这是登出能立即生效的唯一办法。
 *
 * <p>校验失败不直接返回 401，而是保持匿名，交由授权规则决定（/api/auth/** 等公开路径不受影响）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private final TokenPort tokenPort;
    private final TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                TokenPort.Principal principal = tokenPort.parse(token);
                if (tokenBlacklist.isRevoked(principal.tokenId())) {
                    log.debug("令牌已登出吊销，按匿名处理 userId={}", principal.userId());
                } else {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    UserContext.setUserId(principal.userId());
                    // 写入 MDC：后续日志（含访问日志、异常日志）自动带上 user
                    org.slf4j.MDC.put("userId", String.valueOf(principal.userId()));
                }
            } catch (Exception e) {
                // token 无效/过期：保持匿名，由后续授权规则返回 401
                log.debug("JWT 校验失败: {}", e.getMessage());
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            org.slf4j.MDC.remove("userId");
            SecurityContextHolder.clearContext();
        }
    }

    /** 从 Authorization 头取出裸 token */
    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
