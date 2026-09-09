package studio.lingrui.studyagent.infrastructure.persistence.qa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.qa.Question;
import studio.lingrui.studyagent.domain.qa.QuestionRepository;

@Repository
public interface QuestionJpaRepository
        extends JpaRepository<Question, Long>, QuestionRepository {
}
