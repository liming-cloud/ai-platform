package com.aiplatform.foundation.evaluationoperations.infrastructure.staging;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetContentStagingPort;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.StagedEvaluationDatasetContent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在受限目录暂存 UTF-8 JSON Lines，并在任何私有对象写入前验证条目不变量。
 */
public final class LocalEvaluationDatasetContentStagingAdapter
        implements EvaluationDatasetContentStagingPort {

    private static final int BUFFER_SIZE = 8 * 1024;

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
        try (var input = Files.newInputStream(file)) {
            return new JsonLinesEvaluationDatasetEntryReader().read(input, ignored -> { });
        }
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
