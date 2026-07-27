package com.aiplatform.foundation.knowledgedata.infrastructure.staging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.application.datasource.exception.InvalidTextContentException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalTextContentStagingAdapterTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void 流式暂存有效文本并计算字节摘要() throws Exception {
        var adapter = new LocalTextContentStagingAdapter(temporaryDirectory, 1024);
        var bytes = "商品位于一层 A 区，价格 29.90 元。".getBytes(StandardCharsets.UTF_8);

        try (var staged = adapter.stage(new ByteArrayInputStream(bytes))) {
            assertEquals(bytes.length, staged.byteSize());
            assertEquals(
                    "aa8ad0632d14a6a5252f99e6e641cb1499eefb694580ef8cb798b78bc825b39f",
                    staged.sha256());
            try (var input = staged.openStream()) {
                assertArrayEquals(bytes, input.readAllBytes());
            }
        }

        assertEquals(0, fileCount());
    }

    @Test
    void 拒绝畸形Utf8并清理暂存文件() throws Exception {
        var adapter = new LocalTextContentStagingAdapter(temporaryDirectory, 1024);
        var malformed = new byte[] {(byte) 0xE4, (byte) 0xB8};

        assertThrows(
                InvalidTextContentException.class,
                () -> adapter.stage(new ByteArrayInputStream(malformed)));
        assertEquals(0, fileCount());
    }

    @Test
    void 拒绝空内容和超过限制的内容并清理文件() throws Exception {
        var adapter = new LocalTextContentStagingAdapter(temporaryDirectory, 8);

        assertThrows(
                InvalidTextContentException.class,
                () -> adapter.stage(new ByteArrayInputStream(new byte[0])));
        assertThrows(
                InvalidTextContentException.class,
                () -> adapter.stage(new ByteArrayInputStream(new byte[9])));
        assertEquals(0, fileCount());
    }

    private long fileCount() throws Exception {
        try (var files = Files.list(temporaryDirectory)) {
            return files.count();
        }
    }
}
