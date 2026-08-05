package com.aiplatform.foundation.evaluationoperations.infrastructure.staging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证私有评测内容在写入对象存储前由服务端严格解析和计数。
 */
class LocalEvaluationDatasetContentStagingAdapterTest {

    @TempDir
    Path directory;

    @Test
    void 有效JSONLines应保留私有字节并计算摘要和条目数() throws Exception {
        var bytes = ("""
                {"input":"门店营业时间？","expectedEvidenceStableIds":["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"expectedOutcome":"ANSWER","allowedAnswers":["九点至十八点"]}
                {"input":"没有证据时怎么办？","expectedEvidenceStableIds":[],"expectedOutcome":"REFUSE"}
                """).getBytes(StandardCharsets.UTF_8);
        var adapter = new LocalEvaluationDatasetContentStagingAdapter(directory, 1_024L);

        try (var staged = adapter.stage(new ByteArrayInputStream(bytes))) {
            assertEquals(2, staged.entryCount());
            assertEquals(sha256(bytes), staged.contentDigest());
            try (var reopened = staged.openStream()) {
                assertArrayEquals(bytes, reopened.readAllBytes());
            }
        }
    }

    @Test
    void 空行未知字段和不合法条目均必须失败关闭() {
        var adapter = new LocalEvaluationDatasetContentStagingAdapter(directory, 1_024L);

        assertThrows(IllegalArgumentException.class, () -> adapter.stage(content("""
                {"input":"问题","expectedEvidenceStableIds":["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"expectedOutcome":"ANSWER"}

                {"input":"问题","expectedEvidenceStableIds":[],"expectedOutcome":"REFUSE"}
                """)));
        assertThrows(IllegalArgumentException.class, () -> adapter.stage(content("""
                {"input":"问题","expectedEvidenceStableIds":["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"expectedOutcome":"ANSWER","extra":"禁止"}
                """)));
        assertThrows(IllegalArgumentException.class, () -> adapter.stage(content("""
                {"input":"问题","expectedEvidenceStableIds":[],"expectedOutcome":"ANSWER"}
                """)));
        assertThrows(IllegalArgumentException.class, () -> adapter.stage(content("""
                {"input":"问题","expectedEvidenceStableIds":[],"expectedOutcome":"REFUSE","allowedAnswers":["回答"]}
                """)));
        assertThrows(IllegalArgumentException.class, () -> adapter.stage(content("""
                {"input":"问题","expectedEvidenceStableIds":["evidence-001"],"expectedOutcome":"ANSWER"}
                """)));
    }

    private static ByteArrayInputStream content(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
