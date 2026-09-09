package studio.lingrui.studyagent.domain.qa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 题库仓储（领域接口）。
 */
public interface QuestionBankRepository {

    QuestionBank save(QuestionBank bank);

    Optional<QuestionBank> findByIdAndUserId(Long id, Long userId);

    Page<QuestionBank> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    void delete(QuestionBank bank);
}
