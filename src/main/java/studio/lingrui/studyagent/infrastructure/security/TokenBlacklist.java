package studio.lingrui.studyagent.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import studio.lingrui.studyagent.application.port.CachePort;

import java.time.Duration;

/**
 * 令牌吊销名单（jti 黑名单）。
 *
 * <p>无状态 JWT 一旦签发，在有效期内无法"收回"。登出要立即生效，只能把该令牌的
 * {@code jti} 记进 Redis，并在认证时检查——条目 TTL 取令牌自身剩余有效期，
 * 令牌自然过期后条目自动消失，黑名单不会无限增长。
 *
 * <p>Redis 不可用时 {@link CachePort} 会降级（写入丢弃、读取返回空），
 * 此时登出退化为"仅前端清除本地 token"，不影响系统可用性。
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String PREFIX = "auth:revoked:";

    private final CachePort cache;

    /** 吊销一个 token id，保留到令牌自然过期为止 */
    public void revoke(String tokenId, Duration remaining) {
        if (tokenId == null || remaining == null || remaining.isNegative() || remaining.isZero()) {
            return;
        }
        cache.put(PREFIX + tokenId, "1", remaining);
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && cache.get(PREFIX + tokenId, String.class).isPresent();
    }
}
