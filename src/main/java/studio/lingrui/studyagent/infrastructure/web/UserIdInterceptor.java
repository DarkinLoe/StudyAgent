package studio.lingrui.studyagent.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从 X-User-Id 头解析当前用户并写入 {@link UserContext}。
 */
public class UserIdInterceptor implements HandlerInterceptor {

    public static final String HEADER_USER_ID = "X-User-Id";

    private final long defaultUserId;

    public UserIdInterceptor(long defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long userId = defaultUserId;
        String raw = request.getHeader(HEADER_USER_ID);
        if (raw != null && !raw.isBlank()) {
            try {
                userId = Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                // 非法头回退默认用户
            }
        }
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
