package studio.lingrui.studyagent.infrastructure.persistence.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.plan.StudyReminder;
import studio.lingrui.studyagent.domain.plan.StudyReminderRepository;

@Repository
public interface StudyReminderJpaRepository
        extends JpaRepository<StudyReminder, Long>, StudyReminderRepository {
}
