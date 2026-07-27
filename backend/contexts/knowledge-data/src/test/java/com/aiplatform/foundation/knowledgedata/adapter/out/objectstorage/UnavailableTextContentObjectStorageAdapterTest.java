package com.aiplatform.foundation.knowledgedata.adapter.out.objectstorage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.content.ObjectStorageUnavailableException;
import com.aiplatform.foundation.knowledgedata.application.port.out.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UnavailableTextContentObjectStorageAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-27T00:00:00Z");
    private static final String SHA256 = "a".repeat(64);

    private final UnavailableTextContentObjectStorageAdapter adapter =
            new UnavailableTextContentObjectStorageAdapter();

    @Test
    void 未配置对象存储时写入读取和删除均明确失败() {
        var reference = new TextContentReference(
                1,
                "foundation-v1/tenant/space/source/1/content.txt",
                SHA256,
                1,
                TextContentReference.UTF8_TEXT,
                CREATED_AT);

        assertThrows(
                ObjectStorageUnavailableException.class,
                () -> adapter.put(storageRequest()));
        assertThrows(
                ObjectStorageUnavailableException.class,
                () -> adapter.metadata(reference));
        assertThrows(
                ObjectStorageUnavailableException.class,
                () -> adapter.delete(reference));
    }

    private static TextContentStorageRequest storageRequest() {
        return new TextContentStorageRequest(
                new TenantId(100000000000000001L),
                new KnowledgeSpaceId(210000000000000001L),
                new DataSourceId(220000000000000001L),
                1,
                new StagedTextContent() {
                    @Override
                    public long byteSize() {
                        return 1;
                    }

                    @Override
                    public String sha256() {
                        return SHA256;
                    }

                    @Override
                    public ByteArrayInputStream openStream() {
                        return new ByteArrayInputStream(new byte[] {'a'});
                    }

                    @Override
                    public void close() {
                        // 测试桩没有临时资源需要清理。
                    }
                },
                CREATED_AT);
    }
}
