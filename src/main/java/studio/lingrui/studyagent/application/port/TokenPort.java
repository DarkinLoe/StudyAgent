package studio.lingrui.studyagent.application.port;

import java.time.Instant;

/**
 * 令牌端口：应用层只声明"签发/解析/吊销访问令牌"，不依赖 JWT 库与签名算法细节。
 */
public interface TokenPort {

    /** 签发访问令牌 */
    IssuedToken issue(Long userId, String username);

    /**
     * 解析并校验令牌（签名、有效期）。
     *
     * @throws RuntimeException 令牌非法或已过期，由调用方决定如何响应
     */
    Principal parse(String token);

    /**
     * 吊销令牌：在令牌剩余有效期内把它加入黑名单。
     * 无状态 JWT 本身无法撤回，只能靠"服务端记录已吊销的 jti"来兜底。
     */
    void revoke(String token);

    /** 签发结果 */
    record IssuedToken(String token, long ttlMinutes) {
    }

    /** 解析结果 */
    record Principal(Long userId, String username, String tokenId, Instant expiresAt) {
    }
}
