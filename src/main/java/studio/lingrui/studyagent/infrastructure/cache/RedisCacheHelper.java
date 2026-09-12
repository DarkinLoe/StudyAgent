package studio.lingrui.studyagent.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * Redis 缓存助手：值以 JSON 字符串存储，规避泛型序列化器的类型信息坑。
 * 用法示例：get(key, new TypeReference<List<X>>() {}) / set / delete。
 * Redis 不可用时降级为空缓存（不抛错），保证业务主链路可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheHelper {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> T get(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Redis 读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Redis 写入失败 key={}: {}", key, e.getMessage());
        }
    }

    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除失败 key={}: {}", key, e.getMessage());
        }
    }

    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis 前缀删除失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    /**
     * 尝试获取分布式锁（SETNX + TTL），用于多实例部署时串行化定时任务。
     * Redis 不可用时返回 true（放行），避免因缓存故障导致业务任务完全不执行。
     */
    public boolean tryLock(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (Exception e) {
            log.warn("Redis 加锁失败 key={}（放行）: {}", key, e.getMessage());
            return true;
        }
    }

    /** 释放锁（仅用于"持锁者自己释放"的简单场景） */
    public void unlock(String key) {
        delete(key);
    }
}
