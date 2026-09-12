package studio.lingrui.studyagent.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流判定的纯逻辑测试（不需要 Redis）：
 * 脚本返回值语义 + 真实客户端 IP 解析。
 */
class RateLimitFilterTest {

    @Test
    void allowWhenResultWithinLimit() {
        // 脚本允许时返回"包含本次在内的窗口请求数"，第 limit 次仍应放行
        assertFalse(RateLimitFilter.isOverLimit(1L, 20));
        assertFalse(RateLimitFilter.isOverLimit(20L, 20));
    }

    @Test
    void rejectWhenScriptSignalsLimit() {
        // 脚本拒绝时返回 limit+1
        assertTrue(RateLimitFilter.isOverLimit(21L, 20));
    }

    @Test
    void allowWhenRedisUnavailable() {
        // Redis 异常时返回 null：降级放行，不能让限流组件变成可用性单点
        assertFalse(RateLimitFilter.isOverLimit(null, 20));
    }

    @Test
    void clientIpUsesRightmostForwardedEntry() {
        // 前面几段可以由客户端伪造，只有最后一段是自家 Nginx 追加的
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 203.0.113.9");
        assertEquals("203.0.113.9", RateLimitFilter.resolveClientIp(request));
    }

    @Test
    void clientIpIgnoresBlankForwardedSegments() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ,   ");
        request.setRemoteAddr("10.0.0.1");
        assertEquals("10.0.0.1", RateLimitFilter.resolveClientIp(request));
    }

    @Test
    void clientIpFallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest withRealIp = new MockHttpServletRequest();
        withRealIp.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals("198.51.100.7", RateLimitFilter.resolveClientIp(withRealIp));

        MockHttpServletRequest plain = new MockHttpServletRequest();
        plain.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", RateLimitFilter.resolveClientIp(plain));
    }
}
