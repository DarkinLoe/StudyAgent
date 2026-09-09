package studio.lingrui.studyagent.application.qa;

import studio.lingrui.studyagent.domain.qa.QuestionType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目作答判分（选择/判断自动判，填空宽松判等，简答不自动判）。
 */
public final class QuestionGrader {

    private QuestionGrader() {
    }

    /**
     * @return 是否自动判定；自动判定时 correct 才有意义
     */
    public static boolean judge(QuestionType type, String expected, String userAnswer,
                                JudgedResult result) {
        if (expected == null || userAnswer == null) {
            result.correct = false;
            return false;
        }
        String exp = expected.trim();
        String ans = userAnswer.trim();
        switch (type) {
            case SINGLE_CHOICE -> {
                result.correct = exp.equalsIgnoreCase(ans);
                return true;
            }
            case MULTIPLE_CHOICE -> {
                result.correct = sameLetterSet(exp, ans);
                return true;
            }
            case TRUE_FALSE -> {
                result.correct = normalizeTF(exp).equals(normalizeTF(ans));
                return true;
            }
            case FILL_BLANK -> {
                result.correct = exp.equalsIgnoreCase(ans);
                return true;
            }
            case SHORT_ANSWER -> {
                return false; // 简答主观，不自动判
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean sameLetterSet(String a, String b) {
        Set<String> sa = letters(a);
        Set<String> sb = letters(b);
        return !sa.isEmpty() && sa.equals(sb);
    }

    private static Set<String> letters(String s) {
        return Arrays.stream(s.split("[^A-Za-z]+"))
                .filter(x -> !x.isEmpty())
                .map(x -> x.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static String normalizeTF(String s) {
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "对", "正确", "T", "TRUE", "√", "是" -> "T";
            case "错", "错误", "F", "FALSE", "×", "X", "否" -> "F";
            default -> s.toUpperCase(Locale.ROOT);
        };
    }

    public static final class JudgedResult {
        public boolean correct;

        public static JudgedResult of(boolean correct) {
            JudgedResult r = new JudgedResult();
            r.correct = correct;
            return r;
        }
    }
}
