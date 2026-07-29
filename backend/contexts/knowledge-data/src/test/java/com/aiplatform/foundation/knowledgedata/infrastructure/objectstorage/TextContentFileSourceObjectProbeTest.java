package com.aiplatform.foundation.knowledgedata.infrastructure.objectstorage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TextContentFileSourceObjectProbeTest {

    @Test
    void 元数据全部匹配时允许文件探测成功() {
        var storage = mock(TextContentObjectStoragePort.class);
        var revision = revision();
        when(storage.metadata(org.mockito.ArgumentMatchers.any())).thenReturn(new TextContentObjectMetadata(
                revision.fileObject().objectKey(), revision.fileObject().contentDigest(), 12,
                "text/plain; charset=UTF-8"));

        assertDoesNotThrow(() -> new TextContentFileSourceObjectProbe(storage).verify(revision));
    }

    @Test
    void 摘要不一致时拒绝伪成功() {
        var storage = mock(TextContentObjectStoragePort.class);
        var revision = revision();
        when(storage.metadata(org.mockito.ArgumentMatchers.any())).thenReturn(new TextContentObjectMetadata(
                revision.fileObject().objectKey(), "b".repeat(64), 12,
                "text/plain; charset=UTF-8"));

        assertThrows(TextContentIntegrityException.class,
                () -> new TextContentFileSourceObjectProbe(storage).verify(revision));
    }

    private static SourceRevision revision() {
        var source = GovernedDataSource.create(new TenantId(100000000000000001L),
                new KnowledgeSpaceId(210000000000000001L), new DataSourceId(230000000000000001L),
                GovernedDataSourceType.FILE, "文件", new CreatorId(4921825928971026434L),
                Instant.parse("2026-07-29T02:00:00Z"));
        return SourceRevision.file(source, new FileSourceObjectReference(
                "foundation-v1/tenants/1/knowledge-data/files/2/1/content.txt", "a".repeat(64), 12,
                "text/plain; charset=utf-8"), new CreatorId(4921825928971026434L),
                Instant.parse("2026-07-29T02:00:00Z"));
    }
}
