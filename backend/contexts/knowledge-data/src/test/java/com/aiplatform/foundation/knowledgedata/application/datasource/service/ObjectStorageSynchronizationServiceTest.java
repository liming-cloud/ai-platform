package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.synchronization.SynchronizeObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
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
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.ObjectStorageSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ObjectStorageSynchronizationServiceTest {
    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE = new DataSourceId(230000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-30T12:30:00Z");

    @Test
    void 就绪对象存储来源应读取冻结集合并提交私有规范化产物() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class); var reader = mock(PlatformObjectContentReaderPort.class);
        var staging = mock(TextContentStagingPort.class); var storage = mock(TextContentObjectStoragePort.class); var staged = mock(StagedTextContent.class);
        var source = source(); var revision = revision(source); var first = revision.objectStorageObjects().objects().getFirst(); var second = revision.objectStorageObjects().objects().get(1);
        when(repository.findSource(TENANT, SPACE, SOURCE)).thenReturn(Optional.of(source)); when(repository.findRevision(TENANT, SPACE, SOURCE, 1)).thenReturn(Optional.of(revision)); when(repository.update(any(), anyLong())).thenReturn(true);
        when(reader.open(revision, first)).thenReturn(new ByteArrayInputStream("甲".getBytes())); when(reader.open(revision, second)).thenReturn(new ByteArrayInputStream("乙".getBytes()));
        when(staging.stage(any())).thenReturn(staged); when(staged.sha256()).thenReturn("c".repeat(64)); when(staged.byteSize()).thenReturn(8L);
        var stored = new TextContentReference(1, "private/artifact.txt", "c".repeat(64), 8, TextContentReference.UTF8_TEXT, NOW); when(storage.put(any())).thenReturn(stored);

        var result = service(repository, reader, staging, storage).synchronize(command(true, source.version()));

        assertEquals(GovernedDataSourceStatus.READY, result.status()); verify(reader).open(revision, first); verify(reader).open(revision, second); verify(storage).put(any()); verify(repository).completeSynchronization(any());
    }

    @Test
    void 无写权限时不得读取冻结范围或来源事实() {
        var repository = mock(GovernedDataSourceRepository.class); var reader = mock(PlatformObjectContentReaderPort.class); var staging = mock(TextContentStagingPort.class); var storage = mock(TextContentObjectStoragePort.class);
        assertThrows(KnowledgePermissionDeniedException.class, () -> service(repository, reader, staging, storage).synchronize(command(false, 0)));
        verifyNoInteractions(repository, reader, staging, storage);
    }

    private static ObjectStorageSynchronizationService service(GovernedDataSourceRepository repository, PlatformObjectContentReaderPort reader, TextContentStagingPort staging, TextContentObjectStoragePort storage) { return new ObjectStorageSynchronizationService(repository, reader, staging, storage, Clock.fixed(NOW, ZoneOffset.UTC), () -> 240000000000000001L, new ObjectStorageSynchronization()); }
    private static SynchronizeObjectStorageDataSourceCommand command(boolean writable, long version) { return new SynchronizeObjectStorageDataSourceCommand(context(writable), SPACE.toString(), SOURCE.toString(), version); }
    private static GovernedDataSource source() { return GovernedDataSource.create(TENANT, SPACE, SOURCE, GovernedDataSourceType.OBJECT_STORAGE, "对象集合", new CreatorId(4921825928971026434L), NOW).startProbe(0).completeProbe(1); }
    private static SourceRevision revision(GovernedDataSource source) { return SourceRevision.objectStorage(source, new ObjectStorageObjectCollection(new PlatformObjectRangeId("rng_objectstorage1234"), 1, List.of(object("obj_ObjectStorage0001", "a"), object("obj_ObjectStorage0002", "b"))), source.createdBy(), NOW); }
    private static PlatformObjectReference object(String id, String digest) { return new PlatformObjectReference(id, digest.repeat(64), 1, TextContentReference.UTF8_TEXT, NOW); }
    private static TrustedRequestContext context(boolean writable) { return new TrustedRequestContext(TENANT, new SubjectId(4921825928971026434L), new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE, 1, writable ? Set.of(new PermissionCode("knowledge:write")) : Set.of(), "trc-object-storage-sync"); }
}
