package studio.lingrui.studyagent.domain.qa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 题目仓储（领域接口）。
 */
public interface QuestionRepository {

    Question save(Question question);

    Optional<Question> findById(Long id);

    Optional<Question> findByIdAndUserId(Long id, Long userId);

    Page<Question> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<Question> findByUserIdAndBankIdOrderByIdDesc(Long userId, Long bankId, Pageable pageable);

    List<Question> findByIdIn(Collection<Long> ids);

    long countByUserId(Long userId);

    long countByBankId(Long bankId);

    void delete(Question question);
}
