package studio.lingrui.studyagent.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

/**
 * 系统用户（聚合根）。
 *
 * <p>表名用 sys_user：MySQL 中 user 是保留字/内置函数名，避免加反引号的兼容问题。
 * 密码只存 BCrypt 哈希，绝不落地明文。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String username;

    /** BCrypt 哈希（长度固定 60） */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 64)
    private String nickname;

    /** 是否启用（可用于封禁账号） */
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    public static User create(String username, String passwordHash, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setEnabled(true);
        return user;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}
