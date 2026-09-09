package studio.lingrui.studyagent.domain.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

/**
 * 题目（聚合根）：题干 + 选项/答案/解析，可关联来源文档与题库。
 */
@Getter
@Setter
@Entity
@Table(name = "question", indexes = {
        @Index(name = "idx_question_user", columnList = "user_id"),
        @Index(name = "idx_question_bank", columnList = "bank_id")
})
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Question extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属题库 id，可为空（散题） */
    @Column(name = "bank_id")
    private Long bankId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    @Lob
    @Column(nullable = false)
    private String stem;

    /** 选项（JSON 字符串数组，顺序即 A/B/C...）；判断题可为空 */
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    /** 答案：单选 "A"；多选 "A,B"；判断 "对"/"错"；填空/简答为参考答案文本 */
    @Lob
    @Column(nullable = false)
    private String answer;

    @Lob
    @Column
    private String explanation;

    /** 难度 1~5 */
    @Column(nullable = false)
    private Integer difficulty = 3;

    /** 知识点标签（JSON 字符串数组） */
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    /** 来源文档（RAG 提取生成时记录） */
    @Column(name = "source_doc_id")
    private Long sourceDocId;

    public static Question create(Long userId, Long bankId, QuestionType type, String stem,
                                  String optionsJson, String answer, String explanation,
                                  Integer difficulty, String tagsJson, Long sourceDocId) {
        Question q = new Question();
        q.setUserId(userId);
        q.setBankId(bankId);
        q.setType(type);
        q.setStem(stem);
        q.setOptionsJson(optionsJson);
        q.setAnswer(answer);
        q.setExplanation(explanation);
        q.setDifficulty(difficulty == null ? 3 : Math.max(1, Math.min(5, difficulty)));
        q.setTagsJson(tagsJson);
        q.setSourceDocId(sourceDocId);
        return q;
    }

    public void updateContent(QuestionType type, String stem, String optionsJson, String answer,
                              String explanation, Integer difficulty, String tagsJson) {
        this.type = type;
        this.stem = stem;
        this.optionsJson = optionsJson;
        this.answer = answer;
        this.explanation = explanation;
        this.difficulty = difficulty == null ? 3 : Math.max(1, Math.min(5, difficulty));
        this.tagsJson = tagsJson;
    }
}
