package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeSelectionPort;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ObjectStorageDataSourceProbeServiceTest {
    @Test
    void 已登记范围选择仍一致时完成探测并进入就绪状态() {
        var repository = mock(GovernedDataSourceRepository.class);
        var selections = mock(PlatformObjectRangeSelectionPort.class);
        var source = source(); var revision = revision(source);
        when(repository.findSource(any(), any(), any())).thenReturn(java.util.Optional.of(source));
        when(repository.findRevision(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Optional.of(revision));
        when(selections.resolve(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(revision.objectStorageObjects());
        when(repository.update(any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        var service = new ObjectStorageDataSourceProbeService(repository, selections);

        var result = service.probe(new ProbeObjectStorageDataSourceCommand(context(), "202", "404", 0));

        assertEquals("READY", result.status().name()); verify(repository, org.mockito.Mockito.times(2)).update(any(), org.mockito.ArgumentMatchers.anyLong());
    }
    private static GovernedDataSource source() { return GovernedDataSource.create(new TenantId(101), new KnowledgeSpaceId(202), new DataSourceId(404), GovernedDataSourceType.OBJECT_STORAGE, "对象", new CreatorId(303), Instant.parse("2026-07-30T01:00:00Z")); }
    private static SourceRevision revision(GovernedDataSource source) { var c = new ObjectStorageObjectCollection(new PlatformObjectRangeId("rng_7c90d7a8716e4a79"), 7, List.of(new PlatformObjectReference("obj_26d372bcbd4245b7", "a".repeat(64), 1, "text/plain", Instant.parse("2026-07-30T01:00:00Z")))); return SourceRevision.objectStorage(source,c,source.createdBy(),source.createdAt()); }
    private static TrustedRequestContext context() { return new TrustedRequestContext(new TenantId(101),new com.aiplatform.foundation.security.identity.SubjectId(401),new com.aiplatform.foundation.security.identity.MembershipId(303),com.aiplatform.foundation.security.identity.CredentialType.PERSON,com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE,1,Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:write")),"trc-probe"); }
}
