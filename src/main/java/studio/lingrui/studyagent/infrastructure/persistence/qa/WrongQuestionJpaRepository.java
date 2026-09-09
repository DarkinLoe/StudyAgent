package studio.lingrui.studyagent.infrastructure.persistence.qa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.qa.WrongQuestion;
import studio.lingrui.studyagent.domain.qa.WrongQuestionRepository;

@Repository
public interface WrongQuestionJpaRepository
        extends JpaRepository<WrongQuestion, Long>, WrongQuestionRepository {
}
