package studio.lingrui.studyagent.domain.qa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 错题仓储（领域接口）。
 */
public interface WrongQuestionRepository {

    WrongQuestion save(WrongQuestion wrong);

    Optional<WrongQuestion> findByIdAndUserId(Long id, Long userId);

    Optional<WrongQuestion> findByUserIdAndQuestionId(Long userId, Long questionId);

    Page<WrongQuestion> findByUserIdOrderByLastWrongAtDesc(Long userId, Pageable pageable);

    Page<WrongQuestion> findByUserIdAndReviewStatusOrderByLastWrongAtDesc(Long userId, ReviewStatus status,
                                                                          Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndReviewStatus(Long userId, ReviewStatus status);

    void delete(WrongQuestion wrong);
}
