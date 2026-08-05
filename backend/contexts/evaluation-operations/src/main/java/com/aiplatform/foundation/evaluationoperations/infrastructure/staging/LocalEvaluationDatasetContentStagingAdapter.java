package com.aiplatform.foundation.evaluationoperations.infrastructure.staging;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.StagedEvaluationDatasetContent;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 在受限目录暂存 UTF-8 JSON Lines，并在任何私有对象写入前验证条目不变量。
 */
public final class LocalEvaluationDatasetContentStagingAdapter
        implements EvaluationDatasetContentStagingPort {

    private static final int BUFFER_SIZE = 8 * 1024;
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final Path directory;
    private final long maximumBytes;

    public LocalEvaluationDatasetContentStagingAdapter(Path directory, long maximumBytes) {
        this.directory = Objects.requireNonNull(directory, "评测内容暂存目录不能为空")
                .toAbsolutePath()
                .normalize();
        if (maximumBytes < 1L) {
            throw new IllegalArgumentException("评测内容暂存上限必须大于零");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    public StagedEvaluationDatasetContent stage(InputStream input) throws IOException {
        input = Objects.requireNonNull(input, "评测内容输入流不能为空");
        Files.createDirectories(directory);
        Path file = null;
        try {
            file = Files.createTempFile(directory, "foundation-evaluation-", ".upload");
            var digest = sha256();
            long byteSize = 0L;
            try (var output = Files.newOutputStream(file)) {
                var buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (byteSize + read > maximumBytes) {
                        throw new IllegalArgumentException("评测内容超过字节限制");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    byteSize += read;
                }
            }
            if (byteSize == 0L) {
                throw new IllegalArgumentException("评测内容不能为空");
            }
            var entries = validateEntries(file);
            return new FileStagedEvaluationDatasetContent(
                    file,
                    HexFormat.of().formatHex(digest.digest()),
                    entries);
        } catch (IOException | RuntimeException exception) {
            deleteAfterFailure(file, exception);
            throw exception;
        }
    }

    private static int validateEntries(Path file) throws IOException {
        int entries = 0;
        try (var input = Files.newInputStream(file);
                var reader = new BufferedReader(new InputStreamReader(
                        input,
                        StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    throw new IllegalArgumentException("评测内容不能包含空行");
                }
                validateEntry(line);
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

    private static void validateEntry(String line) throws IOException {
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
                            parser,
                            valueToken,
                            "期望证据稳定标识");
                    case "expectedOutcome" -> expectedOutcome = requireText(
                            parser,
                            valueToken,
                            "预期结果");
                    case "allowedAnswers" -> allowedAnswers = readStringArray(
                            parser,
                            valueToken,
                            "允许答案");
                    default -> throw new IllegalArgumentException("评测条目包含未知字段");
                }
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("评测条目只能包含一个 JSON 对象");
            }
            if (input == null || expectedEvidence == null || expectedOutcome == null) {
                throw new IllegalArgumentException("评测条目缺少必填字段");
            }
            if ("ANSWER".equals(expectedOutcome)) {
                if (expectedEvidence.isEmpty()) {
                    throw new IllegalArgumentException("ANSWER 条目必须包含期望证据");
                }
                return;
            }
            if ("REFUSE".equals(expectedOutcome)
                    && expectedEvidence.isEmpty()
                    && allowedAnswers.isEmpty()) {
                return;
            }
            throw new IllegalArgumentException("评测条目预期结果不满足数据集规则");
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

    private static Set<String> readStringArray(
            JsonParser parser,
            JsonToken token,
            String label) throws IOException {
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

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void deleteAfterFailure(Path file, Exception original) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static final class FileStagedEvaluationDatasetContent
            implements StagedEvaluationDatasetContent {

        private final Path file;
        private final String contentDigest;
        private final int entryCount;
        private boolean closed;

        private FileStagedEvaluationDatasetContent(
                Path file,
                String contentDigest,
                int entryCount) {
            this.file = file;
            this.contentDigest = contentDigest;
            this.entryCount = entryCount;
        }

        @Override
        public InputStream openStream() throws IOException {
            if (closed) {
                throw new IllegalStateException("评测内容暂存已关闭");
            }
            return Files.newInputStream(file);
        }

        @Override
        public String contentDigest() {
            return contentDigest;
        }

        @Override
        public int entryCount() {
            return entryCount;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                Files.deleteIfExists(file);
            }
        }
    }
}
