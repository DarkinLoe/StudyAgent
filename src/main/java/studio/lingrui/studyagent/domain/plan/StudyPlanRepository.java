package studio.lingrui.studyagent.domain.plan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 学习计划仓储（领域接口）。
 */
public interface StudyPlanRepository {

    StudyPlan save(StudyPlan plan);

    Optional<StudyPlan> findByIdAndUserId(Long id, Long userId);

    Page<StudyPlan> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    List<StudyPlan> findByIdIn(Collection<Long> ids);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, StudyPlanStatus status);

    void delete(StudyPlan plan);
}
