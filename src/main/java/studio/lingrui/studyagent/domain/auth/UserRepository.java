package studio.lingrui.studyagent.domain.auth;

import java.util.Optional;

/**
 * 用户仓储（领域接口）。
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
