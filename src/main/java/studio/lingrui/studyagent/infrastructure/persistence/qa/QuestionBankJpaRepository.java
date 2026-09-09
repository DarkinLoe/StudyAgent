package studio.lingrui.studyagent.infrastructure.persistence.qa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.qa.QuestionBank;
import studio.lingrui.studyagent.domain.qa.QuestionBankRepository;

@Repository
public interface QuestionBankJpaRepository
        extends JpaRepository<QuestionBank, Long>, QuestionBankRepository {
}
