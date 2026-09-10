package studio.lingrui.studyagent.application.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordSnippetExtractorTest {

    @Test
    void extractsSnippetAroundKeyword() {
        String text = "第一章讲矩阵的定义。第二章讲矩阵的乘法与逆矩阵，重点是乘法的结合律。";
        String snippet = KeywordSnippetExtractor.extract(text, "乘法", 8);
        assertNotNull(snippet);
        assertTrue(snippet.contains("乘法"));
        assertTrue(snippet.startsWith("…"), "关键词在中间时应带前置省略号");
    }

    @Test
    void matchIsCaseInsensitiveForLatin() {
        String snippet = KeywordSnippetExtractor.extract("Spring Boot RAG Pipeline", "rag", 5);
        assertNotNull(snippet);
        assertTrue(snippet.contains("RAG"));
    }

    @Test
    void missingKeywordReturnsNull() {
        assertNull(KeywordSnippetExtractor.extract("线性代数", "概率论", 10));
        assertNull(KeywordSnippetExtractor.extract(null, "x", 10));
        assertNull(KeywordSnippetExtractor.extract("abc", "  ", 10));
    }

    @Test
    void snippetIsTrimmedAndWhitespaceCollapsed() {
        String text = "开头\n\n   关键词   附近有   很多空白   结尾";
        String snippet = KeywordSnippetExtractor.extract(text, "关键词", 6);
        assertNotNull(snippet);
        assertTrue(!snippet.contains("\n"));
        assertTrue(!snippet.contains("  "));
    }

    @Test
    void tokenizeSplitsOnSpacesAndPunctuation() {
        List<String> tokens = KeywordSnippetExtractor.tokenize("矩阵 乘法，逆矩阵；determinant!");
        assertEquals(List.of("矩阵", "乘法", "逆矩阵", "determinant"), tokens);
    }

    @Test
    void tokenizeBlankQueryReturnsEmpty() {
        assertTrue(KeywordSnippetExtractor.tokenize(null).isEmpty());
        assertTrue(KeywordSnippetExtractor.tokenize("   ").isEmpty());
    }
}
