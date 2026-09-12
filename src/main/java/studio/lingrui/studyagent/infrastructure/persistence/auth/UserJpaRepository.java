package studio.lingrui.studyagent.infrastructure.persistence.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.auth.User;
import studio.lingrui.studyagent.domain.auth.UserRepository;

@Repository
public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {
}
