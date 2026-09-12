package studio.lingrui.studyagent.infrastructure.security;

/**
 * 认证主体：从 JWT 解析出的当前登录用户。
 */
public record AuthPrincipal(Long userId, String username) {
}
