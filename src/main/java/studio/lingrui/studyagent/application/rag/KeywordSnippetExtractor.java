package studio.lingrui.studyagent.application.rag;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 关键词片段抽取（无 Embedding 能力时的降级检索基础能力）。
 *
 * <p>纯函数、可单测：定位关键词在原文中的位置并截取上下文窗口。
 */
public final class KeywordSnippetExtractor {

    private KeywordSnippetExtractor() {
    }

    /**
     * 抽取关键词附近的片段。
     *
     * @param window 关键词前后各取多少字符
     * @return 命中片段（含省略号标记）；未命中返回 null
     */
    public static String extract(String text, String keyword, int window) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return null;
        }
        int idx = text.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return null;
        }
        int start = Math.max(0, idx - Math.max(window, 0));
        int end = Math.min(text.length(), idx + keyword.length() + Math.max(window, 0));
        String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "…" : "") + snippet + (end < text.length() ? "…" : "");
    }

    /**
     * 把查询拆成关键词（空格/中英文标点分隔），用于关键词匹配。
     */
    public static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.split("[\\s,，。;；、!！?？:：()（）\\[\\]\"']+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
