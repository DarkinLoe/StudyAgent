package studio.lingrui.studyagent.infrastructure.web;

/**
 * 当前请求用户上下文（ThreadLocal）。
 * 登录体系上线前通过请求头 X-User-Id 指定用户，缺省用配置里的默认用户。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        Long id = USER_ID.get();
        return id == null ? 1L : id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
