package studio.lingrui.studyagent.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import studio.lingrui.studyagent.application.port.CachePort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis 缓存适配器：值统一以 JSON 字符串存储，规避泛型序列化器的类型信息坑。
 *
 * <p>三个工程要点：
 * <ol>
 *   <li><b>全部方法都不抛异常</b>：Redis 故障时降级（读空、写丢、锁放行），
 *       缓存是加速层，不能变成可用性单点；</li>
 *   <li><b>前缀失效用 SCAN 游标分批删除</b>，不用 {@code KEYS}——Redis 单线程下
 *       {@code KEYS} 会阻塞所有请求，是生产事故常客；</li>
 *   <li><b>释放锁走 Lua 原子比对</b>：先查 value 再删的两步操作之间锁可能已过期并被
 *       别的实例重新获取，直接 DEL 会删掉别人的锁；Lua 脚本在服务端原子执行，
 *       只有 token 仍然匹配时才删除。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {

    /** 只有"值仍等于我的 token"时才删除，避免误删他人重新获取的锁 */
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                    """, Long.class);

    /** 每批删除的 key 数量：避免一次性构造超大命令，也避免长期占用内存 */
    private static final int DELETE_BATCH = 500;
    private static final long SCAN_COUNT = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Redis 读取失败 key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)));
        } catch (Exception e) {
            log.warn("Redis 读取失败 key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis 写入失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除失败 key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 按前缀失效。用 SCAN 游标而非 KEYS：KEYS 会一次性遍历整个 keyspace，
     * 在 Redis 单线程模型下直接阻塞其它所有命令。
     */
    @Override
    public void evictByPrefix(String prefix) {
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(SCAN_COUNT)
                    .build();
            List<String> batch = new ArrayList<>(DELETE_BATCH);
            try (Cursor<String> cursor = redis.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= DELETE_BATCH) {
                        redis.delete(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                redis.delete(batch);
            }
        } catch (Exception e) {
            log.warn("Redis 前缀删除失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    @Override
    public Optional<Lock> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? Optional.of(new Lock(key, token)) : Optional.empty();
        } catch (Exception e) {
            log.warn("Redis 加锁失败 key={}（降级放行）: {}", key, e.getMessage());
            return Optional.of(new Lock(key, token));
        }
    }

    @Override
    public void release(Lock lock) {
        if (lock == null) {
            return;
        }
        try {
            redis.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lock.key()), lock.token());
        } catch (Exception e) {
            log.warn("Redis 解锁失败 key={}: {}", lock.key(), e.getMessage());
        }
    }
}
