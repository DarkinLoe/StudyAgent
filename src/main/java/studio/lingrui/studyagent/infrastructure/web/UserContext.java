package studio.lingrui.studyagent.infrastructure.web;

import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

/**
 * 当前请求用户上下文（ThreadLocal）。
 * 由 {@code JwtAuthFilter} 在认证成功后写入；业务代码通过 {@link #getUserId()} 获取当前用户。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 当前登录用户 id；未认证时抛 401（避免"静默当成默认用户"导致越权）。
     */
    public static Long getUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return id;
    }

    /** 不抛异常的读取（日志、监控等非业务场景用） */
    public static Long peekUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
