package studio.lingrui.studyagent.application.auth;

/**
 * 登录/注册结果（对前端暴露的最小集合，不含任何密码信息）。
 */
public record AuthResult(
        String token,
        long expiresInMinutes,
        Long userId,
        String username,
        String nickname
) {
}
