package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleAction;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
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

class GovernedDataSourceLifecycleServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);

    @Test
    void 具有知识写权限的主体可开始探测并返回新修订() {
        var repository = mock(GovernedDataSourceRepository.class);
        var source = source();
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.update(any(GovernedDataSource.class), org.mockito.ArgumentMatchers.eq(0L))).thenReturn(true);
        var service = new GovernedDataSourceLifecycleService(repository);

        var result = service.transition(new GovernedDataSourceLifecycleCommand(
                context(true),
                SPACE.toString(),
                SOURCE_ID.toString(),
                0,
                GovernedDataSourceLifecycleAction.START_PROBE));

        assertEquals(GovernedDataSourceStatus.PROBING, result.status());
        assertEquals(1, result.version());
        verify(repository).update(any(GovernedDataSource.class), org.mockito.ArgumentMatchers.eq(0L));
    }

    @Test
    void 无知识写权限时拒绝且不读取来源事实() {
        var repository = mock(GovernedDataSourceRepository.class);
        var service = new GovernedDataSourceLifecycleService(repository);

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.transition(
                new GovernedDataSourceLifecycleCommand(
                        context(false),
                        SPACE.toString(),
                        SOURCE_ID.toString(),
                        0,
                        GovernedDataSourceLifecycleAction.START_PROBE)));

        verifyNoInteractions(repository);
    }

    @Test
    void 乐观更新失败时拒绝返回成功状态() {
        var repository = mock(GovernedDataSourceRepository.class);
        var source = source();
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.update(any(GovernedDataSource.class), org.mockito.ArgumentMatchers.eq(0L))).thenReturn(false);
        var service = new GovernedDataSourceLifecycleService(repository);

        assertThrows(DataSourceConcurrentModificationException.class, () -> service.transition(
                new GovernedDataSourceLifecycleCommand(
                        context(true),
                        SPACE.toString(),
                        SOURCE_ID.toString(),
                        0,
                        GovernedDataSourceLifecycleAction.START_PROBE)));
    }

    private static GovernedDataSource source() {
        return GovernedDataSource.create(
                TENANT,
                SPACE,
                SOURCE_ID,
                GovernedDataSourceType.FILE,
                "受控文件",
                new CreatorId(4921825928971026434L),
                Instant.parse("2026-07-29T02:00:00Z"));
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                writable ? Set.of(new PermissionCode("knowledge:write")) : Set.of(),
                "trc-governed-source");
    }
}
