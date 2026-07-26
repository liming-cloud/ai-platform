package com.aiplatform.foundation.knowledgedata.domain.indexversion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * `paragraph-window-v1` 确定性分块算法。
 */
public final class ParagraphWindowChunker {

    public static final String POLICY = "paragraph-window-v1";
    public static final int WINDOW_CODE_POINTS = 800;
    public static final int OVERLAP_CODE_POINTS = 80;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public List<TextChunk> chunk(String sourceDigest, String text) {
        requireDigest(sourceDigest);
        Objects.requireNonNull(text, "待分块文本不能为空");
        var normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }

        var values = new ArrayList<TextChunk>();
        for (var paragraph : paragraphs(normalized)) {
            appendWindows(sourceDigest, paragraph, values);
        }
        return List.copyOf(values);
    }

    private static String normalize(String value) {
        var lineNormalized = value
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if (!lineNormalized.isEmpty() && lineNormalized.charAt(0) == '\uFEFF') {
            lineNormalized = lineNormalized.substring(1);
        }
        var unicodeNormalized = Normalizer.normalize(lineNormalized, Normalizer.Form.NFC);
        var lines = unicodeNormalized.split("\n", -1);
        var first = 0;
        var last = lines.length;
        while (first < last && lines[first].isBlank()) {
            first++;
        }
        while (last > first && lines[last - 1].isBlank()) {
            last--;
        }
        return String.join("\n", List.of(lines).subList(first, last));
    }

    private static List<String> paragraphs(String normalized) {
        var paragraphs = new ArrayList<String>();
        var current = new ArrayList<String>();
        for (var line : normalized.split("\n", -1)) {
            if (line.isBlank()) {
                appendParagraph(current, paragraphs);
            } else {
                current.add(line);
            }
        }
        appendParagraph(current, paragraphs);
        return paragraphs;
    }

    private static void appendParagraph(
            List<String> current,
            List<String> paragraphs) {
        if (!current.isEmpty()) {
            paragraphs.add(String.join("\n", current));
            current.clear();
        }
    }

    private static void appendWindows(
            String sourceDigest,
            String paragraph,
            List<TextChunk> chunks) {
        var codePoints = paragraph.codePointCount(0, paragraph.length());
        var start = 0;
        while (start < codePoints) {
            var end = Math.min(start + WINDOW_CODE_POINTS, codePoints);
            var startOffset = paragraph.offsetByCodePoints(0, start);
            var endOffset = paragraph.offsetByCodePoints(0, end);
            appendChunk(
                    sourceDigest,
                    paragraph.substring(startOffset, endOffset),
                    chunks);
            if (end == codePoints) {
                return;
            }
            start = end - OVERLAP_CODE_POINTS;
        }
    }

    private static void appendChunk(
            String sourceDigest,
            String text,
            List<TextChunk> chunks) {
        var ordinal = chunks.size();
        var textDigest = sha256(text);
        var id = sha256(
                sourceDigest
                        + "\u0000"
                        + POLICY
                        + "\u0000"
                        + ordinal
                        + "\u0000"
                        + textDigest);
        chunks.add(new TextChunk(id, ordinal, text, textDigest));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void requireDigest(String value) {
        Objects.requireNonNull(value, "来源摘要不能为空");
        if (!SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException("来源摘要格式不正确");
        }
    }
}
