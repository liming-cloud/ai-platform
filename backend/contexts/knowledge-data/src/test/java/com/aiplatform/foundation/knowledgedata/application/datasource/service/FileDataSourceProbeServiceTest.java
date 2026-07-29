package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.FileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FileDataSourceProbeServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);

    @Test
    void 探测在事务外核验冻结对象后进入就绪状态() {
        var repository = mock(GovernedDataSourceRepository.class);
        var probe = mock(FileSourceObjectProbe.class);
        var source = source();
        var revision = revision(source);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findRevision(TENANT, SPACE, SOURCE_ID, 1)).thenReturn(Optional.of(revision));
        when(repository.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        var service = new FileDataSourceProbeService(repository, probe);

        var result = service.probe(command(true, 0));

        assertEquals(GovernedDataSourceStatus.READY, result.status());
        assertEquals(2, result.version());
        var order = inOrder(repository, probe);
        order.verify(repository).update(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.status() == GovernedDataSourceStatus.PROBING),
                org.mockito.ArgumentMatchers.eq(0L));
        order.verify(repository).findRevision(TENANT, SPACE, SOURCE_ID, 1);
        order.verify(probe).verify(revision);
        order.verify(repository).update(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.status() == GovernedDataSourceStatus.READY),
                org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void 无知识写权限时探测前拒绝且不读取来源或对象() {
        var repository = mock(GovernedDataSourceRepository.class);
        var probe = mock(FileSourceObjectProbe.class);
        var service = new FileDataSourceProbeService(repository, probe);

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.probe(command(false, 0)));

        verifyNoInteractions(repository, probe);
    }

    @Test
    void 对象核验失败只登记探测失败并保留恢复语义() {
        var repository = mock(GovernedDataSourceRepository.class);
        var probe = mock(FileSourceObjectProbe.class);
        var source = source();
        var revision = revision(source);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findRevision(TENANT, SPACE, SOURCE_ID, 1)).thenReturn(Optional.of(revision));
        when(repository.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("对象不可用"))
                .when(probe).verify(revision);
        var service = new FileDataSourceProbeService(repository, probe);

        var result = service.probe(command(true, 0));

        assertEquals(GovernedDataSourceStatus.FAILED, result.status());
        assertEquals(2, result.version());
    }

    @Test
    void 探测失败后重新探测先恢复探测状态再核验冻结对象() {
        var repository = mock(GovernedDataSourceRepository.class);
        var probe = mock(FileSourceObjectProbe.class);
        var failed = source().startProbe(0).failProbe(1);
        var revision = revision(failed);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(failed));
        when(repository.findRevision(TENANT, SPACE, SOURCE_ID, 1)).thenReturn(Optional.of(revision));
        when(repository.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);
        var service = new FileDataSourceProbeService(repository, probe);

        var result = service.probe(command(true, 2));

        assertEquals(GovernedDataSourceStatus.READY, result.status());
        assertEquals(4, result.version());
        var order = inOrder(repository, probe);
        order.verify(repository).update(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.status() == GovernedDataSourceStatus.PROBING),
                org.mockito.ArgumentMatchers.eq(2L));
        order.verify(repository).findRevision(TENANT, SPACE, SOURCE_ID, 1);
        order.verify(probe).verify(revision);
        order.verify(repository).update(org.mockito.ArgumentMatchers.argThat(
                candidate -> candidate.status() == GovernedDataSourceStatus.READY),
                org.mockito.ArgumentMatchers.eq(3L));
    }

    @Test
    void 同步失败不能被文件探测入口恢复且不访问对象() {
        var repository = mock(GovernedDataSourceRepository.class);
        var probe = mock(FileSourceObjectProbe.class);
        var synchronizationFailure = source().startProbe(0)
                .completeProbe(1)
                .startSynchronization(2)
                .failSynchronization(3);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID))
                .thenReturn(Optional.of(synchronizationFailure));
        var service = new FileDataSourceProbeService(repository, probe);

        assertThrows(DataSourceStateException.class, () -> service.probe(command(true, 4)));

        verifyNoInteractions(probe);
        verify(repository, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verify(repository, org.mockito.Mockito.never())
                .findRevision(TENANT, SPACE, SOURCE_ID, 1);
    }

    @Test
    void 探测开始状态未写入时不执行对象访问() {
        var repository = mock(GovernedDataSourceRepository.class);
        var probe = mock(FileSourceObjectProbe.class);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source()));
        when(repository.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0L)))
                .thenReturn(false);
        var service = new FileDataSourceProbeService(repository, probe);

        assertThrows(DataSourceConcurrentModificationException.class, () -> service.probe(command(true, 0)));

        verifyNoInteractions(probe);
    }

    private static ProbeFileDataSourceCommand command(boolean writable, long version) {
        return new ProbeFileDataSourceCommand(context(writable), SPACE.toString(), SOURCE_ID.toString(), version);
    }

    private static GovernedDataSource source() {
        return GovernedDataSource.create(TENANT, SPACE, SOURCE_ID, GovernedDataSourceType.FILE,
                "受控文件", new CreatorId(4921825928971026434L), Instant.parse("2026-07-29T02:00:00Z"));
    }

    private static SourceRevision revision(GovernedDataSource source) {
        return SourceRevision.file(source, new FileSourceObjectReference(
                "foundation-v1/tenants/1/knowledge-data/files/2/1/content.txt", "a".repeat(64), 12,
                "text/plain; charset=utf-8"), new CreatorId(4921825928971026434L), Instant.parse("2026-07-29T02:00:00Z"));
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(TENANT, new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE, 1,
                writable ? Set.of(new PermissionCode("knowledge:write")) : Set.of(), "trc-file-probe");
    }
}
