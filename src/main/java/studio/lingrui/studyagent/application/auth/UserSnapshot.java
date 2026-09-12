package studio.lingrui.studyagent.application.auth;

import studio.lingrui.studyagent.domain.auth.User;

/**
 * 用户资料快照：缓存的扁平只读视图。
 *
 * <p>为什么不直接缓存 JPA 实体：
 * <ul>
 *   <li>实体带乐观锁版本列、持久化上下文语义与延迟加载属性，类结构一变缓存就反序列化失败；</li>
 *   <li>实体里含 passwordHash —— 不该让它长期驻留在缓存里；</li>
 *   <li>快照字段是显式的，接口演进时不会"顺手"把新字段泄漏进缓存。</li>
 * </ul>
 * 凭证（密码哈希）刻意不进缓存：登录是低频操作，且缓存它会导致改密/封禁在 TTL 内不生效。
 */
public record UserSnapshot(Long id, String username, String nickname, boolean enabled) {

    public static UserSnapshot of(User user) {
        return new UserSnapshot(user.getId(), user.getUsername(), user.getNickname(),
                Boolean.TRUE.equals(user.getEnabled()));
    }
}
