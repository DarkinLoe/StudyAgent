package studio.lingrui.studyagent.infrastructure.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void shortTextSingleChunk() {
        List<String> chunks = TextChunker.split("只有一句话", 800, 100);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("只有一句话"));
    }

    @Test
    void longTextSplitsOnBoundary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("第").append(i).append("段内容。");
        }
        List<String> chunks = TextChunker.split(sb.toString(), 100, 20);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);
        // 拼接后应能还原原文主干（允许重叠）
        StringBuilder merged = new StringBuilder();
        chunks.forEach(merged::append);
        assertTrue(merged.length() >= sb.length());
    }

    @Test
    void blankInputNoChunks() {
        assertTrue(TextChunker.split("   \n ", 100, 10).isEmpty());
        assertTrue(TextChunker.split(null, 100, 10).isEmpty());
    }

    @Test
    void tinyChunkNoInfiniteLoop() {
        // 分块上限被内部 clamp 到至少 100，仍应正常返回
        String text = "abcdefghij".repeat(50);
        List<String> chunks = TextChunker.split(text, 10, 200);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 1);
    }
}
