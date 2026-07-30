package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.SynchronizeStructuredDatasetCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvProjection;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvProjectionPort;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.StructuredDatasetCsvSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructuredDatasetCsvSynchronizationServiceTest {
    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE = new DataSourceId(230000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    @Test void 就绪结构化数据集应投影写入私有文本并原子提交领域结果() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class); var projection = mock(StructuredDatasetCsvProjectionPort.class);
        var staging = mock(TextContentStagingPort.class); var storage = mock(TextContentObjectStoragePort.class); var staged = mock(StagedTextContent.class);
        var source = source(); var revision = revision(source); var syncing = source.startSynchronization(source.version());
        when(repository.findSource(TENANT, SPACE, SOURCE)).thenReturn(Optional.of(source)); when(repository.findRevision(TENANT, SPACE, SOURCE, 1)).thenReturn(Optional.of(revision)); when(repository.update(any(), anyLong())).thenReturn(true);
        when(projection.project(revision.structuredDataset())).thenReturn(new StructuredDatasetCsvProjection("标题\n正文", 1, 0)); when(staging.stage(any())).thenReturn(staged); when(staged.sha256()).thenReturn("a".repeat(64)); when(staged.byteSize()).thenReturn(13L);
        var stored = new TextContentReference(1, "private/artifact.txt", "a".repeat(64), 13, TextContentReference.UTF8_TEXT, NOW); when(storage.put(any())).thenReturn(stored);

        var result = service(repository, projection, staging, storage).synchronize(command(true, source.version()));

        assertEquals(GovernedDataSourceStatus.READY, result.status()); verify(projection).project(revision.structuredDataset()); verify(storage).put(any()); verify(repository).completeSynchronization(any());
    }

    @Test void 无写权限时应在读取来源前拒绝() {
        var repository = mock(GovernedDataSourceRepository.class); var projection = mock(StructuredDatasetCsvProjectionPort.class); var staging = mock(TextContentStagingPort.class); var storage = mock(TextContentObjectStoragePort.class);
        assertThrows(KnowledgePermissionDeniedException.class, () -> service(repository, projection, staging, storage).synchronize(command(false, 0)));
        verifyNoInteractions(repository, projection, staging, storage);
    }

    @Test void 提交失败时应删除未提交文本并登记同步失败() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class); var projection = mock(StructuredDatasetCsvProjectionPort.class);
        var staging = mock(TextContentStagingPort.class); var storage = mock(TextContentObjectStoragePort.class); var staged = mock(StagedTextContent.class);
        var source = source(); var revision = revision(source);
        when(repository.findSource(TENANT, SPACE, SOURCE)).thenReturn(Optional.of(source)); when(repository.findRevision(TENANT, SPACE, SOURCE, 1)).thenReturn(Optional.of(revision)); when(repository.update(any(), anyLong())).thenReturn(true);
        when(projection.project(revision.structuredDataset())).thenReturn(new StructuredDatasetCsvProjection("标题\n正文", 1, 0)); when(staging.stage(any())).thenReturn(staged); when(staged.sha256()).thenReturn("a".repeat(64)); when(staged.byteSize()).thenReturn(13L);
        var stored = new TextContentReference(1, "private/artifact.txt", "a".repeat(64), 13, TextContentReference.UTF8_TEXT, NOW); when(storage.put(any())).thenReturn(stored); doThrow(new IllegalStateException("提交失败")).when(repository).completeSynchronization(any());
        var result = service(repository, projection, staging, storage).synchronize(command(true, source.version()));
        assertEquals(GovernedDataSourceStatus.FAILED, result.status()); verify(storage).delete(stored); verify(repository).update(org.mockito.ArgumentMatchers.argThat(value -> value.status() == GovernedDataSourceStatus.FAILED), anyLong());
    }

    private static StructuredDatasetCsvSynchronizationService service(GovernedDataSourceRepository repository, StructuredDatasetCsvProjectionPort projection, TextContentStagingPort staging, TextContentObjectStoragePort storage) { return new StructuredDatasetCsvSynchronizationService(repository, projection, staging, storage, Clock.fixed(NOW, ZoneOffset.UTC), () -> 240000000000000001L, new StructuredDatasetCsvSynchronization()); }
    private static SynchronizeStructuredDatasetCommand command(boolean writable, long version) { return new SynchronizeStructuredDatasetCommand(context(writable), SPACE.toString(), SOURCE.toString(), version); }
    private static GovernedDataSource source() { return GovernedDataSource.create(TENANT, SPACE, SOURCE, GovernedDataSourceType.STRUCTURED_DATASET, "数据集", new CreatorId(4921825928971026434L), NOW).startProbe(0).completeProbe(1); }
    private static SourceRevision revision(GovernedDataSource source) { return SourceRevision.structuredDataset(source, new StructuredDatasetCsvDefinition(new StructuredDatasetCsvReference("private/dataset.csv", "b".repeat(64), 20), "csv-mapping-v1", "content", "title", List.of(new StructuredDatasetFieldMapping("content", StructuredDatasetLogicalType.TEXT, StructuredDatasetSensitivityLevel.PUBLIC), new StructuredDatasetFieldMapping("title", StructuredDatasetLogicalType.TEXT, StructuredDatasetSensitivityLevel.PUBLIC)), StructuredDatasetRowFilter.ALL_ROWS), source.createdBy(), NOW); }
    private static TrustedRequestContext context(boolean writable) { return new TrustedRequestContext(TENANT, new SubjectId(4921825928971026434L), new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE, 1, writable ? Set.of(new PermissionCode("knowledge:write")) : Set.of(), "trc-dataset-sync"); }
}
