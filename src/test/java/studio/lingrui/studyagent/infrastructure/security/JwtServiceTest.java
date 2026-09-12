package studio.lingrui.studyagent.infrastructure.security;

import org.junit.jupiter.api.Test;
import studio.lingrui.studyagent.application.port.CachePort;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 签发/解析/吊销测试：
 * ① jti 必须存在，否则登出吊销无从下手；② 登出后 jti 应进入黑名单；③ 已过期令牌解析失败；
 * ④ 密钥太短必须启动即失败（而不是悄悄用弱密钥签发）。
 */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 字节

    @Test
    void issuedTokenCarriesJtiAndParsesBack() {
        TokenBlacklist blacklist = new TokenBlacklist(new InMemoryCache());
        JwtService service = new JwtService(props(SECRET, 30), blacklist);

        JwtService.IssuedToken issued = service.issue(42L, "alice");
        JwtService.Principal principal = service.parse(issued.token());

        assertEquals(42L, principal.userId());
        assertEquals("alice", principal.username());
        assertNotNull(principal.tokenId(), "令牌必须带 jti，否则无法吊销");
        assertFalse(principal.tokenId().isBlank());
        assertNotNull(principal.expiresAt());
        assertEquals(30, issued.ttlMinutes());
    }

    @Test
    void revokedTokenIsMarkedInBlacklist() {
        TokenBlacklist blacklist = new TokenBlacklist(new InMemoryCache());
        JwtService service = new JwtService(props(SECRET, 30), blacklist);
        String token = service.issue(1L, "bob").token();
        String tokenId = service.parse(token).tokenId();

        assertFalse(blacklist.isRevoked(tokenId));
        service.revoke(token);
        assertTrue(blacklist.isRevoked(tokenId), "登出后 jti 必须进入黑名单");
    }

    @Test
    void expiredTokenIsRejected() {
        // TTL 0 分钟 => 签发即过期
        JwtService service = new JwtService(props(SECRET, 0), new TokenBlacklist(new InMemoryCache()));
        String token = service.issue(1L, "carol").token();

        assertThrows(RuntimeException.class, () -> service.parse(token));
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new JwtService(props("too-short", 30), new TokenBlacklist(new InMemoryCache())));
        assertTrue(e.getMessage().contains("32"), "应提示密钥长度要求：" + e.getMessage());
    }

    private StudyAgentProperties props(String secret, long ttlMinutes) {
        StudyAgentProperties props = new StudyAgentProperties();
        props.getSecurity().setJwtSecret(secret);
        props.getSecurity().setTokenTtlMinutes(ttlMinutes);
        return props;
    }

    /** 内存版缓存：只实现本测试用得到的读写，避免依赖 Redis */
    private static final class InMemoryCache implements CachePort {

        private final Map<String, Object> store = new HashMap<>();

        @Override
        public <T> Optional<T> get(String key, Class<T> type) {
            return Optional.ofNullable(store.get(key)).map(type::cast);
        }

        @Override
        public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            store.put(key, value);
        }

        @Override
        public void evict(String key) {
            store.remove(key);
        }

        @Override
        public void evictByPrefix(String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Lock> tryLock(String key, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void release(Lock lock) {
            throw new UnsupportedOperationException();
        }
    }
}
