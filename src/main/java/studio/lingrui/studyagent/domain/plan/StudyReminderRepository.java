package studio.lingrui.studyagent.domain.plan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 学习提醒仓储（领域接口）。
 */
public interface StudyReminderRepository {

    StudyReminder save(StudyReminder reminder);

    Optional<StudyReminder> findByIdAndUserId(Long id, Long userId);

    Page<StudyReminder> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<StudyReminder> findByUserIdAndReadFlagFalseOrderByIdAsc(Long userId);

    long countByUserIdAndReadFlagFalse(Long userId);

    long countByUserId(Long userId);
}
