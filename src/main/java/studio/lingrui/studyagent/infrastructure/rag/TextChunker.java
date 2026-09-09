package studio.lingrui.studyagent.infrastructure.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 朴素文本分块：按段落优先、其次句子边界切分，带重叠，避免切断语义块。
 */
public final class TextChunker {

    private TextChunker() {
    }

    public static List<String> split(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").trim();
        int size = Math.max(chunkSize, 100);
        int overlap = Math.min(Math.max(chunkOverlap, 0), size / 2);
        int start = 0;
        int len = normalized.length();
        while (start < len) {
            int end = Math.min(start + size, len);
            if (end < len) {
                // 在边界窗口内找最接近的段落/句子断点
                int boundary = lastBoundary(normalized, start, end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= len) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private static int lastBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == ';' || c == '；') {
                return i + 1;
            }
        }
        return end;
    }
}
