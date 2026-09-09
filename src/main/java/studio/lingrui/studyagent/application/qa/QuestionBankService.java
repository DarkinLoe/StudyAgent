package studio.lingrui.studyagent.application.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.qa.QuestionBank;
import studio.lingrui.studyagent.domain.qa.QuestionBankRepository;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

/**
 * 题库管理。
 */
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionBankRepository banks;

    @Transactional
    public QuestionBank create(Long userId, String name, String description) {
        return banks.save(QuestionBank.create(userId, name, description));
    }

    public Page<QuestionBank> list(Long userId, int page, int size) {
        return banks.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public QuestionBank detail(Long userId, Long bankId) {
        return banks.findByIdAndUserId(bankId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "题库不存在"));
    }

    @Transactional
    public QuestionBank update(Long userId, Long bankId, String name, String description) {
        QuestionBank bank = detail(userId, bankId);
        bank.rename(name, description);
        return banks.save(bank);
    }

    @Transactional
    public void delete(Long userId, Long bankId) {
        QuestionBank bank = detail(userId, bankId);
        banks.delete(bank);
    }
}
