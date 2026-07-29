package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.SynchronizeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FileDataSourceSynchronizationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-29T05:00:00Z");

    @Test
    void 就绪文件在事务外核验冻结内容后原子登记产物和质量结论() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var source = readySource();
        var content = "可信同步内容".getBytes(StandardCharsets.UTF_8);
        var revision = revision(source, content);
        var syncing = source.startSynchronization(source.version());
        var completed = syncing.completeSynchronization(syncing.version());
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findRevision(TENANT, SPACE, SOURCE_ID, 1)).thenReturn(Optional.of(revision));
        when(repository.update(any(), anyLong())).thenReturn(true);
        when(storage.read(any())).thenReturn(new ByteArrayInputStream(content));
        when(repository.completeSynchronization(any(), any(), eq(syncing.version()))).thenReturn(completed);
        var service = service(repository, storage);

        var result = service.synchronize(command(true, source.version()));

        assertEquals(GovernedDataSourceStatus.READY, result.status());
        assertEquals(completed.version(), result.version());
        assertEquals("240000000000000001", result.syncArtifactId());
        assertEquals(QualityStatus.PASS, result.qualityStatus());
        var order = inOrder(repository, storage);
        order.verify(repository).update(any(), eq(source.version()));
        order.verify(repository).findRevision(TENANT, SPACE, SOURCE_ID, 1);
        order.verify(storage).read(any());
        order.verify(repository).completeSynchronization(
                org.mockito.ArgumentMatchers.argThat(artifact -> artifact.contentDigest().equals(sha256(content))
                        && artifact.recordCount() == 1),
                org.mockito.ArgumentMatchers.argThat(report -> report.status().name().equals("PASS")
                        && report.ruleSetVersion().equals("file-integrity-v1")),
                eq(syncing.version()));
    }

    @Test
    void 冻结对象内容摘要不一致时只登记同步失败且不写产物() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var source = readySource();
        var revision = revision(source, "原始内容".getBytes(StandardCharsets.UTF_8));
        var syncing = source.startSynchronization(source.version());
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findRevision(TENANT, SPACE, SOURCE_ID, 1)).thenReturn(Optional.of(revision));
        when(repository.update(any(), anyLong())).thenReturn(true);
        when(storage.read(any())).thenReturn(new ByteArrayInputStream("替换内容".getBytes(StandardCharsets.UTF_8)));
        var service = service(repository, storage);

        var result = service.synchronize(command(true, source.version()));

        assertEquals(GovernedDataSourceStatus.FAILED, result.status());
        assertEquals(syncing.failSynchronization(syncing.version()).version(), result.version());
        assertEquals(DataSourceOperation.SYNCHRONIZATION, result.failedOperation());
        verify(repository).update(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.status() == GovernedDataSourceStatus.FAILED
                        && candidate.failedOperation() == DataSourceOperation.SYNCHRONIZATION),
                eq(syncing.version()));
        verify(repository, org.mockito.Mockito.never()).completeSynchronization(any(), any(), anyLong());
    }

    @Test
    void 同步失败后仅按同步语义恢复并可重新完成() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var content = "恢复后的同步内容".getBytes(StandardCharsets.UTF_8);
        var source = readySource().startSynchronization(2).failSynchronization(3);
        var revision = revision(source, content);
        var syncing = source.retry(source.version());
        var completed = syncing.completeSynchronization(syncing.version());
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findRevision(TENANT, SPACE, SOURCE_ID, 1)).thenReturn(Optional.of(revision));
        when(repository.update(any(), anyLong())).thenReturn(true);
        when(storage.read(any())).thenReturn(new ByteArrayInputStream(content));
        when(repository.completeSynchronization(any(), any(), eq(syncing.version()))).thenReturn(completed);
        var service = service(repository, storage);

        var result = service.synchronize(command(true, source.version()));

        assertEquals(GovernedDataSourceStatus.READY, result.status());
        assertEquals(completed.version(), result.version());
        verify(repository).update(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.status() == GovernedDataSourceStatus.SYNCING), eq(source.version()));
    }

    @Test
    void 无知识写权限时同步前拒绝且不访问来源或对象() {
        var repository = mock(GovernedDataSourceRepository.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var service = service(repository, storage);

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.synchronize(command(false, 2)));

        verifyNoInteractions(repository, storage);
    }

    private static FileDataSourceSynchronizationService service(
            GovernedDataSourceRepository repository, TextContentObjectStoragePort storage) {
        return new FileDataSourceSynchronizationService(
                repository,
                storage,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 240000000000000001L);
    }

    private static SynchronizeFileDataSourceCommand command(boolean writable, long version) {
        return new SynchronizeFileDataSourceCommand(context(writable), SPACE.toString(), SOURCE_ID.toString(), version);
    }

    private static GovernedDataSource readySource() {
        return GovernedDataSource.create(TENANT, SPACE, SOURCE_ID, GovernedDataSourceType.FILE,
                "受控文件", new CreatorId(4921825928971026434L), NOW).startProbe(0).completeProbe(1);
    }

    private static SourceRevision revision(GovernedDataSource source, byte[] content) {
        return SourceRevision.file(source, new FileSourceObjectReference(
                "foundation-v1/tenants/1/knowledge-data/files/2/1/content.txt", sha256(content), content.length,
                FileSourceObjectReference.UTF8_TEXT), source.createdBy(), NOW);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(TENANT, new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                writable ? Set.of(new PermissionCode("knowledge:write")) : Set.of(), "trc-file-sync");
    }
}
