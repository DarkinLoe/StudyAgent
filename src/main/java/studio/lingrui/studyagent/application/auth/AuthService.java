package studio.lingrui.studyagent.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.application.port.CachePort;
import studio.lingrui.studyagent.application.port.TokenPort;
import studio.lingrui.studyagent.domain.auth.User;
import studio.lingrui.studyagent.domain.auth.UserRepository;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.time.Duration;

/**
 * 认证用例：注册 / 登录 / 当前用户 / 登出。
 *
 * <p>并发与安全要点：
 * <ul>
 *   <li>用户名唯一约束由数据库兜底（uk_user_username），并发注册时捕获冲突并返回友好错误；</li>
 *   <li>登录失败统一提示"用户名或密码错误"，不区分用户是否存在（防用户枚举）；</li>
 *   <li>登录失败次数限制由 {@code RateLimitFilter} 在入口处按真实客户端 IP 完成；</li>
 *   <li><b>登录凭证不进缓存</b>：只缓存扁平的用户资料快照。
 *       缓存 passwordHash 会让"改密/封禁"在 TTL 内不生效，登录本身是低频操作，
 *       按唯一索引查一次库完全够用；</li>
 *   <li>登出把当前令牌的 jti 加入黑名单（无状态 JWT 无法撤回，这是登出立即生效的唯一办法）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int USERNAME_MIN = 3;
    private static final int USERNAME_MAX = 32;
    private static final int PASSWORD_MIN = 6;
    private static final String CACHE_USER_BY_ID = "cache:user:id:";
    private static final Duration PROFILE_TTL = Duration.ofMinutes(5);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;
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
        User user = users.findByUsername(name)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return toResult(user);
    }

    /**
     * 当前用户资料（走 Redis 快照缓存，TTL 5 分钟）。
     * 缓存的是 {@link UserSnapshot} 而不是 JPA 实体：实体含乐观锁列与持久化语义，
     * 直接序列化进 Redis 既脆弱又会把 passwordHash 一起长期存下来。
     */
    @Transactional(readOnly = true)
    public UserSnapshot require(Long userId) {
        String key = CACHE_USER_BY_ID + userId;
        UserSnapshot cached = cache.get(key, UserSnapshot.class).orElse(null);
        if (cached != null) {
            return cached;
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在"));
        UserSnapshot snapshot = UserSnapshot.of(user);
        cache.put(key, snapshot, PROFILE_TTL);
        return snapshot;
    }

    /** 登出：吊销当前令牌（按 jti 加黑名单，存活到令牌自然过期） */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        tokenPort.revoke(token);
    }

    private AuthResult toResult(User user) {
        TokenPort.IssuedToken issued = tokenPort.issue(user.getId(), user.getUsername());
        return new AuthResult(issued.token(), issued.ttlMinutes(),
                user.getId(), user.getUsername(), user.getNickname());
    }
}
