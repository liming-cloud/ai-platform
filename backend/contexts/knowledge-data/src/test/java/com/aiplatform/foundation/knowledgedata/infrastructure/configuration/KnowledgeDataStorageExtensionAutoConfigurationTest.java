package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderExtensionPort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectReadRequest;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeDataStorageExtensionAutoConfigurationTest {
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void 只有冻结集合成员可传给对象存储扩展() {
        var extension = mock(PlatformObjectContentReaderExtensionPort.class);
        var reader = new KnowledgeDataStorageExtensionAutoConfiguration()
                .platformObjectReaderBridge(extension);
        var member = object("obj_ObjectStorage001", "a");
        var revision = revision(member);
        var foreign = object("obj_ObjectStorage002", "b");

        assertThrows(IllegalArgumentException.class, () -> reader.open(revision, foreign));
        verifyNoInteractions(extension);

        var stream = new ByteArrayInputStream(new byte[] {1});
        when(extension.open(any())).thenReturn(stream);
        assertSame(stream, reader.open(revision, member));
        var capture = ArgumentCaptor.forClass(PlatformObjectReadRequest.class);
        verify(extension).open(capture.capture());
        var request = capture.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(revision.tenantId().value(), request.tenantId());
        org.junit.jupiter.api.Assertions.assertEquals(member.contentDigest(), request.contentDigest());
    }

    private static PlatformObjectReference object(String id, String digest) {
        return new PlatformObjectReference(id, digest.repeat(64), 1,
                "text/plain; charset=UTF-8", NOW);
    }

    private static SourceRevision revision(PlatformObjectReference object) {
        var collection = new ObjectStorageObjectCollection(
                new PlatformObjectRangeId("rng_ObjectStorage001"), 1, List.of(object));
        return SourceRevision.restore(new TenantId(100000000000000001L),
                new KnowledgeSpaceId(210000000000000001L), new DataSourceId(220000000000000001L),
                1, GovernedDataSourceType.OBJECT_STORAGE, collection.definitionDigest(),
                null, collection, null, new CreatorId(100000000000000001L), NOW);
    }
}
