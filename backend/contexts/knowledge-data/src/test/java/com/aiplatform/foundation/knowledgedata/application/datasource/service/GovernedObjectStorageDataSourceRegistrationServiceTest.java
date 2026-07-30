package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeSelectionPort;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GovernedObjectStorageDataSourceRegistrationServiceTest {

    @Test
    void 有知识写权限时仅登记平台已核验的范围选择和对象集合() {
        var repository = mock(GovernedDataSourceRepository.class);
        var selections = mock(PlatformObjectRangeSelectionPort.class);
        var collection = collection();
        when(selections.resolve(any(), any(), any(), anyLong())).thenReturn(collection);
        var service = new GovernedObjectStorageDataSourceRegistrationService(
                repository, selections, () -> 230000000000000001L,
                java.time.Clock.fixed(Instant.parse("2026-07-30T03:00:00Z"), java.time.ZoneOffset.UTC));

        var result = service.register(new RegisterObjectStorageDataSourceCommand(
                context(true), "210000000000000001", "平台对象来源", "rng_7c90d7a8716e4a79", 7));

        assertEquals("DRAFT", result.status().name());
        verify(selections).resolve(any(), any(), any(), anyLong());
        verify(repository).register(any(), any());
    }

    @Test
    void 无知识写权限时不解析范围也不登记() {
        var repository = mock(GovernedDataSourceRepository.class);
        var selections = mock(PlatformObjectRangeSelectionPort.class);
        var service = new GovernedObjectStorageDataSourceRegistrationService(
                repository, selections, () -> 230000000000000001L, java.time.Clock.systemUTC());

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.register(
                new RegisterObjectStorageDataSourceCommand(
                        context(false), "210000000000000001", "平台对象来源", "rng_7c90d7a8716e4a79", 7)));

        verifyNoInteractions(repository, selections);
    }

    private static ObjectStorageObjectCollection collection() {
        return new ObjectStorageObjectCollection(new PlatformObjectRangeId("rng_7c90d7a8716e4a79"), 7,
                List.of(new PlatformObjectReference("obj_26d372bcbd4245b7", "a".repeat(64), 128,
                        "text/plain; charset=utf-8", Instant.parse("2026-07-30T01:00:00Z"))));
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(new com.aiplatform.foundation.shared.kernel.tenant.TenantId(100000000000000001L),
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new com.aiplatform.foundation.security.identity.MembershipId(3382738893456539650L),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE, 1,
                writable ? Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:write")) : Set.of(),
                "trc-object-storage-register");
    }
}
