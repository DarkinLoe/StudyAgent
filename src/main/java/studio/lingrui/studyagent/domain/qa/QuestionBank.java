package studio.lingrui.studyagent.domain.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

/**
 * 题库（聚合根）：按科目/考试等维度组织的个人题库。
 */
@Getter
@Setter
@Entity
@Table(name = "question_bank")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class QuestionBank extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    public static QuestionBank create(Long userId, String name, String description) {
        QuestionBank bank = new QuestionBank();
        bank.setUserId(userId);
        bank.setName(name);
        bank.setDescription(description);
        return bank;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
