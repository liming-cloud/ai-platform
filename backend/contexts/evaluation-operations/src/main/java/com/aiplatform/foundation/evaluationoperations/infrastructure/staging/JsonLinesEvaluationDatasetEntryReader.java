package com.aiplatform.foundation.evaluationoperations.infrastructure.staging;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetEntryReader;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetEntry;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 以严格 UTF-8 JSON Lines 逐条解析私有评测内容；不缓存或持久化条目正文。
 */
public final class JsonLinesEvaluationDatasetEntryReader implements EvaluationDatasetEntryReader {

    private static final Pattern CHUNK_ID = Pattern.compile("[0-9a-f]{64}");
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Override
    public int read(InputStream content, Consumer<EvaluationDatasetEntry> consumer) throws IOException {
        if (content == null || consumer == null) {
            throw new IllegalArgumentException("评测内容或消费者不能为空");
        }
        int entries = 0;
        try (var reader = new BufferedReader(new InputStreamReader(
                content,
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    throw new IllegalArgumentException("评测内容不能包含空行");
                }
                consumer.accept(readEntry(line));
                if (entries == Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("评测内容条目数超出上限");
                }
                entries++;
            }
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("评测内容必须是严格 UTF-8", exception);
        }
        if (entries == 0) {
            throw new IllegalArgumentException("评测内容必须至少包含一个条目");
        }
        return entries;
    }

    private static EvaluationDatasetEntry readEntry(String line) throws IOException {
        try (JsonParser parser = JSON.createParser(line)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("评测条目必须是 JSON 对象");
            }
            String input = null;
            Set<String> expectedEvidence = null;
            String expectedOutcome = null;
            Set<String> allowedAnswers = Set.of();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new IllegalArgumentException("评测条目字段格式不正确");
                }
                var field = parser.currentName();
                var valueToken = parser.nextToken();
                if (valueToken == null) {
                    throw new IllegalArgumentException("评测条目字段值缺失");
                }
                switch (field) {
                    case "input" -> input = requireText(parser, valueToken, "评测输入");
                    case "expectedEvidenceStableIds" -> expectedEvidence = readStringArray(
                            parser, valueToken, "期望证据稳定标识");
                    case "expectedOutcome" -> expectedOutcome = requireText(parser, valueToken, "预期结果");
                    case "allowedAnswers" -> allowedAnswers = readStringArray(parser, valueToken, "允许答案");
                    default -> throw new IllegalArgumentException("评测条目包含未知字段");
                }
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("评测条目只能包含一个 JSON 对象");
            }
            if (input == null || expectedEvidence == null || expectedOutcome == null) {
                throw new IllegalArgumentException("评测条目缺少必填字段");
            }
            if (expectedEvidence.stream().anyMatch(value -> !CHUNK_ID.matcher(value).matches())) {
                throw new IllegalArgumentException("期望证据稳定标识必须是分块标识");
            }
            return new EvaluationDatasetEntry(
                    input,
                    expectedEvidence,
                    EvaluationDatasetEntry.ExpectedOutcome.valueOf(expectedOutcome),
                    allowedAnswers);
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    private static String requireText(JsonParser parser, JsonToken token, String label) throws IOException {
        if (token != JsonToken.VALUE_STRING) {
            throw new IllegalArgumentException(label + "必须是字符串");
        }
        var value = parser.getText();
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
        return value;
    }

    private static Set<String> readStringArray(JsonParser parser, JsonToken token, String label) throws IOException {
        if (token != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException(label + "必须是数组");
        }
        var values = new LinkedHashSet<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                throw new IllegalArgumentException(label + "成员必须是字符串");
            }
            var value = parser.getText();
            if (value == null || value.isBlank() || !value.equals(value.trim())) {
                throw new IllegalArgumentException(label + "成员格式不正确");
            }
            if (!values.add(value)) {
                throw new IllegalArgumentException(label + "不能重复");
            }
        }
        return Set.copyOf(values);
    }
}
