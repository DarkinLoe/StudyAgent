package studio.lingrui.studyagent.domain.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 错题记录（同一用户同一题去重累积，状态机驱动复习）。
 */
@Getter
@Setter
@Entity
@Table(name = "wrong_question",
        uniqueConstraints = @UniqueConstraint(name = "uk_wrong_user_question", columnNames = {"user_id", "question_id"}),
        indexes = @Index(name = "idx_wrong_user", columnList = "user_id"))
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WrongQuestion extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 用户当时的作答 */
    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    /** 答错次数（累计） */
    @Column(name = "mistake_count", nullable = false)
    private Integer mistakeCount = 1;

    @Column(name = "last_wrong_at", nullable = false)
    private LocalDateTime lastWrongAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    public static WrongQuestion firstWrong(Long userId, Long questionId, String userAnswer) {
        WrongQuestion w = new WrongQuestion();
        w.setUserId(userId);
        w.setQuestionId(questionId);
        w.setUserAnswer(userAnswer);
        w.setMistakeCount(1);
        w.setLastWrongAt(LocalDateTime.now());
        w.setReviewStatus(ReviewStatus.PENDING);
        return w;
    }

    /** 再次答错：累计次数并复位为待复习 */
    public void wrongAgain(String userAnswer) {
        this.mistakeCount = this.mistakeCount + 1;
        this.userAnswer = userAnswer;
        this.lastWrongAt = LocalDateTime.now();
        this.reviewStatus = ReviewStatus.PENDING;
    }

    /** 答对时推进复习状态（仅当确实待复习才自动降级处理由服务决定） */
    public void reviewPassed() {
        this.lastWrongAt = LocalDateTime.now();
    }

    public void markStatus(ReviewStatus status) {
        this.reviewStatus = status;
    }
}
