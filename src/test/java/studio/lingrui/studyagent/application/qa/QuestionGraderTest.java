package studio.lingrui.studyagent.application.qa;

import org.junit.jupiter.api.Test;
import studio.lingrui.studyagent.domain.qa.QuestionType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionGraderTest {

    @Test
    void singleChoiceIgnoreCase() {
        QuestionGrader.JudgedResult r = QuestionGrader.JudgedResult.of(false);
        boolean auto = QuestionGrader.judge(QuestionType.SINGLE_CHOICE, "A", "a", r);
        assertTrue(auto);
        assertTrue(r.correct);
    }

    @Test
    void multiChoiceSameSet() {
        QuestionGrader.JudgedResult ok = QuestionGrader.JudgedResult.of(false);
        assertTrue(QuestionGrader.judge(QuestionType.MULTIPLE_CHOICE, "A,B", "b, a", ok));
        assertTrue(ok.correct);

        QuestionGrader.JudgedResult bad = QuestionGrader.JudgedResult.of(true);
        QuestionGrader.judge(QuestionType.MULTIPLE_CHOICE, "A,B", "A,C", bad);
        assertFalse(bad.correct);
    }

    @Test
    void trueFalseNormalize() {
        QuestionGrader.JudgedResult ok = QuestionGrader.JudgedResult.of(false);
        assertTrue(QuestionGrader.judge(QuestionType.TRUE_FALSE, "对", "正确", ok));
        assertTrue(ok.correct);

        QuestionGrader.JudgedResult wrong = QuestionGrader.JudgedResult.of(false);
        QuestionGrader.judge(QuestionType.TRUE_FALSE, "对", "错", wrong);
        assertFalse(wrong.correct);
    }

    @Test
    void shortAnswerNotAutoJudged() {
        QuestionGrader.JudgedResult r = QuestionGrader.JudgedResult.of(false);
        boolean auto = QuestionGrader.judge(QuestionType.SHORT_ANSWER, "参考答案", "学生回答", r);
        assertFalse(auto);
    }
}
