package studio.lingrui.studyagent.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.auth.User;
import studio.lingrui.studyagent.domain.auth.UserRepository;
import studio.lingrui.studyagent.application.port.CachePort;
import studio.lingrui.studyagent.infrastructure.security.JwtService;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.time.Duration;
import java.util.Optional;

/**
 * 认证用例：注册 / 登录 / 查询当前用户。
 *
 * <p>并发与安全要点：
 * <ul>
 *   <li>用户名唯一约束由数据库兜底（uk_user_username），并发注册时捕获冲突并返回友好错误；</li>
 *   <li>登录失败统一提示"用户名或密码错误"，不区分用户是否存在（防用户枚举）；</li>
 *   <li>登录失败次数限制由 {@code RateLimitFilter} 在入口处完成（Redis 计数）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 32;
    private static final int PASSWORD_MIN = 6;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CachePort cache;

    @Transactional
    public AuthResult register(String username, String rawPassword, String nickname) {
        String name = username == null ? "" : username.trim();
        if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名长度需在 " + USERNAME_MIN + "~" + USERNAME_MAX + " 之间");
        }
        if (rawPassword == null || rawPassword.length() < PASSWORD_MIN) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码至少 " + PASSWORD_MIN + " 位");
        }
        if (users.existsByUsername(name)) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        try {
            User user = users.save(User.create(name, passwordEncoder.encode(rawPassword), nickname));
            return toResult(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发注册同一用户名：数据库唯一约束兜底
            throw new BizException(ErrorCode.CONFLICT, "用户名已被占用");
        }
    }

    @Transactional(readOnly = true)
    public AuthResult login(String username, String rawPassword) {
        String name = username == null ? "" : username.trim();
        User user = findCachedByUsername(name)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return toResult(user);
    }

    @Transactional(readOnly = true)
    public User require(Long userId) {
        String key = "cache:user:id:" + userId;
        User cached = cache.get(key, User.class).orElse(null);
        if (cached != null) {
            return cached;
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在"));
        cache.put(key, user, Duration.ofMinutes(10));
        return user;
    }

    /** 按用户名查用户（走 Redis 缓存，降低登录/鉴权热路径的 DB 压力） */
    private Optional<User> findCachedByUsername(String username) {
        String key = "cache:user:name:" + username;
        User cached = cache.get(key, User.class).orElse(null);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<User> found = users.findByUsername(username);
        found.ifPresent(user -> cache.put(key, user, Duration.ofMinutes(10)));
        return found;
    }

    private AuthResult toResult(User user) {
        String token = jwtService.issue(user.getId(), user.getUsername());
        return new AuthResult(token, jwtService.getTtlMinutes(),
                user.getId(), user.getUsername(), user.getNickname());
    }
}
