package studio.lingrui.studyagent.domain.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 笔记仓储（领域接口）。
 */
public interface StudyNoteRepository {

    StudyNote save(StudyNote note);

    Optional<StudyNote> findByIdAndUserId(Long id, Long userId);

    Page<StudyNote> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    void delete(StudyNote note);
}
