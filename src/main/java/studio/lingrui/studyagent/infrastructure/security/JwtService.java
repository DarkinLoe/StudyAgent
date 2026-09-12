package studio.lingrui.studyagent.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.shared.config.StudyAgentProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验（HS256 对称密钥）。
 *
 * <p>密钥来源：{@code study-agent.security.jwt-secret}（建议用环境变量 JWT_SECRET 注入，>=32 字节）。
 * 未配置时启动生成临时随机密钥并告警——进程重启后旧 token 全部失效，仅适合本地开发。
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(StudyAgentProperties props) {
        String secret = props.getSecurity().getJwtSecret();
        this.ttlMinutes = props.getSecurity().getTokenTtlMinutes();
        if (secret == null || secret.isBlank()) {
            this.key = Jwts.SIG.HS256.key().build();
            log.warn("未配置 study-agent.security.jwt-secret，已生成临时随机密钥："
                    + "重启后所有 token 失效。生产请通过环境变量 JWT_SECRET 注入至少 32 字节的密钥。");
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("study-agent.security.jwt-secret 至少需要 32 字节（当前 " + bytes.length + "）");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
    }

    /** 签发访问令牌 */
    public String issue(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(ttlMinutes))))
                .signWith(key)
                .compact();
    }

    /** 校验并解析令牌；无效/过期抛异常（由调用方决定如何处理） */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthPrincipal(Long.valueOf(claims.getSubject()),
                claims.get("username", String.class));
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }
}
