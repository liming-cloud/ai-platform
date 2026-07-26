package com.aiplatform.foundation.knowledgedata.adapter.out.staging;

import com.aiplatform.foundation.knowledgedata.application.content.InvalidTextContentException;
import com.aiplatform.foundation.knowledgedata.application.port.out.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStagingPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 在受限临时目录流式暂存并严格校验 UTF-8 文本。
 */
public final class LocalTextContentStagingAdapter implements TextContentStagingPort {

    private static final int BUFFER_SIZE = 8192;

    private final Path directory;
    private final long maximumBytes;

    public LocalTextContentStagingAdapter(Path directory, long maximumBytes) {
        this.directory = Objects.requireNonNull(directory, "暂存目录不能为空")
                .toAbsolutePath()
                .normalize();
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("文本字节限制必须大于零");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    public StagedTextContent stage(InputStream input) throws IOException {
        Objects.requireNonNull(input, "文本输入流不能为空");
        Files.createDirectories(directory);
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(
                    directory,
                    "foundation-text-",
                    ".upload");
            var digest = sha256();
            long byteSize = 0;
            try (var output = Files.newOutputStream(temporaryFile)) {
                var buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (byteSize + read > maximumBytes) {
                        throw new InvalidTextContentException("文本内容超过字节限制");
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    byteSize += read;
                }
            }
            if (byteSize == 0) {
                throw new InvalidTextContentException("文本内容不能为空");
            }
            validateUtf8(temporaryFile);
            return new FileStagedTextContent(
                    temporaryFile,
                    byteSize,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException exception) {
            deleteAfterFailure(temporaryFile, exception);
            throw exception;
        }
    }

    private static void validateUtf8(Path file) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new InputStreamReader(Files.newInputStream(file), decoder)) {
            var buffer = new char[BUFFER_SIZE];
            while (reader.read(buffer) != -1) {
                // 只验证字符流，不在堆内保存原始内容。
            }
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new InvalidTextContentException("文本内容不是有效 UTF-8", exception);
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

    private static final class FileStagedTextContent implements StagedTextContent {

        private final Path file;
        private final long byteSize;
        private final String sha256;
        private boolean closed;

        private FileStagedTextContent(Path file, long byteSize, String sha256) {
            this.file = file;
            this.byteSize = byteSize;
            this.sha256 = sha256;
        }

        @Override
        public long byteSize() {
            return byteSize;
        }

        @Override
        public String sha256() {
            return sha256;
        }

        @Override
        public InputStream openStream() throws IOException {
            if (closed) {
                throw new IOException("暂存内容已经清理");
            }
            return Files.newInputStream(file);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                throw new IllegalStateException("无法清理文本暂存文件", exception);
            } finally {
                closed = true;
            }
        }
    }
}
