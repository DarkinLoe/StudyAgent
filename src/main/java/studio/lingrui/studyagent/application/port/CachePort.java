package studio.lingrui.studyagent.application.port;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 缓存端口：由应用层定义、基础设施层实现（依赖倒置）。
 *
 * <p>之所以要抽象：应用服务只关心"给我一个按 key 的读写缓存 + 一把分布式锁"，
 * 不关心背后是 Redis、Caffeine 还是别的实现，也不该 import 任何 Redis/Jackson 类型。
 * 之前的写法是应用层直接依赖 infrastructure 里的 RedisCacheHelper，
 * 并让 Jackson 的 {@code TypeReference} 泄漏到用例代码里。
 *
 * <p>约定：<b>所有方法都不抛异常</b>。缓存是可选的加速层，Redis 故障时必须降级
 * （读返回空、写丢弃、加锁放行），不能把业务主链路一起拖挂。
 */
public interface CachePort {

    /** 读取单个对象；不存在或缓存不可用时返回 {@link Optional#empty()} */
    <T> Optional<T> get(String key, Class<T> type);

    /** 读取列表；不存在或缓存不可用时返回 {@link Optional#empty()} */
    <T> Optional<List<T>> getList(String key, Class<T> elementType);

    void put(String key, Object value, Duration ttl);

    void evict(String key);

    /** 按前缀失效（实现需避免阻塞式全库扫描） */
    void evictByPrefix(String prefix);

    /**
     * 尝试获取分布式锁（非阻塞）。
     *
     * @return 拿到锁时返回锁句柄，锁已被他人持有时返回 {@link Optional#empty()}；
     *         缓存不可用时<b>放行</b>（返回句柄），宁可重复执行也不漏执行
     */
    Optional<Lock> tryLock(String key, Duration ttl);

    /** 释放锁；必须校验锁归属，避免误删别人重新获取的锁 */
    void release(Lock lock);

    /**
     * 锁句柄。
     *
     * @param key   锁的 Redis key
     * @param token 持有者标识，释放时用于比对（只有自己加的锁才能删）
     */
    record Lock(String key, String token) {
    }
}
