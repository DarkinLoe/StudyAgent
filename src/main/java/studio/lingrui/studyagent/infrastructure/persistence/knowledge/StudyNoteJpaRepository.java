package studio.lingrui.studyagent.infrastructure.persistence.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.knowledge.StudyNote;
import studio.lingrui.studyagent.domain.knowledge.StudyNoteRepository;

@Repository
public interface StudyNoteJpaRepository
        extends JpaRepository<StudyNote, Long>, StudyNoteRepository {
}
