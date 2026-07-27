package com.aiplatform.foundation.knowledgedata.domain.indexversion.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ParagraphWindowChunkerTest {

    private final ParagraphWindowChunker chunker = new ParagraphWindowChunker();

    @Test
    void 相同语义换行和组合字符生成相同分块与标识() throws Exception {
        var decomposed = "\uFEFF标题\r\n\r\nCafe\u0301\r\n位置：一层";
        var normalized = "标题\n\nCafé\n位置：一层";
        var sourceDigest = sha256(normalized.getBytes(StandardCharsets.UTF_8));

        var first = chunker.chunk(sourceDigest, decomposed);
        var second = chunker.chunk(sourceDigest, normalized);

        assertEquals(second, first);
        assertEquals(2, first.size());
        assertEquals("标题", first.get(0).text());
        assertEquals("Café\n位置：一层", first.get(1).text());
        assertEquals(0, first.get(0).ordinal());
        assertEquals(1, first.get(1).ordinal());
    }

    @Test
    void 长段落按码点窗口切分并保留八十码点重叠() throws Exception {
        var text = "商".repeat(790) + "😀".repeat(30);
        var chunks = chunker.chunk(
                sha256(text.getBytes(StandardCharsets.UTF_8)),
                text);

        assertEquals(2, chunks.size());
        assertEquals(800, chunks.get(0).text().codePointCount(
                0,
                chunks.get(0).text().length()));
        assertEquals(100, chunks.get(1).text().codePointCount(
                0,
                chunks.get(1).text().length()));
        var firstTail = chunks.get(0).text().substring(
                chunks.get(0).text().offsetByCodePoints(
                        0,
                        chunks.get(0).text().codePointCount(
                                0,
                                chunks.get(0).text().length()) - 80));
        var secondHead = chunks.get(1).text().substring(
                0,
                chunks.get(1).text().offsetByCodePoints(0, 80));
        assertEquals(firstTail, secondHead);
        assertTrue(Character.isSurrogate(chunks.get(0).text().charAt(
                chunks.get(0).text().length() - 1)));
    }

    @Test
    void 来源摘要或分块内容变化时分块标识变化() throws Exception {
        var first = chunker.chunk("a".repeat(64), "记录一").getFirst();
        var changedSource = chunker.chunk("b".repeat(64), "记录一").getFirst();
        var changedText = chunker.chunk("a".repeat(64), "记录二").getFirst();

        assertNotEquals(first.id(), changedSource.id());
        assertNotEquals(first.id(), changedText.id());
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }
}
