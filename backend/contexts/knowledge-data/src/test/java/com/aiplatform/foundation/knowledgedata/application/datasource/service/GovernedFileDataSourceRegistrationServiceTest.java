package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GovernedFileDataSourceRegistrationServiceTest {

    @Test
    void 有知识写权限时平台保存内容并原子登记文件修订() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var staging = mock(TextContentStagingPort.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var staged = mock(StagedTextContent.class);
        when(staging.stage(any())).thenReturn(staged);
        when(staged.sha256()).thenReturn("a".repeat(64));
        when(staged.byteSize()).thenReturn(12L);
        when(storage.put(any())).thenReturn(new TextContentReference(1,
                "foundation-v1/tenants/100000000000000001/knowledge-data/files/1/1/content.txt",
                "a".repeat(64), 12, TextContentReference.UTF8_TEXT, Instant.parse("2026-07-29T03:00:00Z")));
        var service = new GovernedFileDataSourceRegistrationService(repository, staging, storage,
                () -> 230000000000000001L, java.time.Clock.fixed(Instant.parse("2026-07-29T03:00:00Z"), java.time.ZoneOffset.UTC));

        var result = service.register(new RegisterFileDataSourceCommand(context(true), "210000000000000001",
                "文件来源", new ByteArrayInputStream("测试内容".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        assertEquals("DRAFT", result.status().name());
        assertEquals(0, result.version());
        verify(storage).put(any());
        verify(repository).register(any(), any());
    }

    @Test
    void 无知识写权限时不暂存不写对象也不登记() {
        var repository = mock(GovernedDataSourceRepository.class);
        var staging = mock(TextContentStagingPort.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var service = new GovernedFileDataSourceRegistrationService(repository, staging, storage,
                () -> 230000000000000001L, java.time.Clock.systemUTC());

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.register(
                new RegisterFileDataSourceCommand(context(false), "210000000000000001", "文件来源",
                        new ByteArrayInputStream(new byte[] {1}))));

        verifyNoInteractions(repository, staging, storage);
    }

    @Test
    void 登记持久化失败时删除刚写入的平台对象() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var staging = mock(TextContentStagingPort.class);
        var storage = mock(TextContentObjectStoragePort.class);
        var staged = mock(StagedTextContent.class);
        var stored = new TextContentReference(1,
                "foundation-v1/tenants/100000000000000001/knowledge-data/files/1/1/content.txt",
                "a".repeat(64), 12, TextContentReference.UTF8_TEXT, Instant.parse("2026-07-29T03:00:00Z"));
        when(staging.stage(any())).thenReturn(staged);
        when(storage.put(any())).thenReturn(stored);
        org.mockito.Mockito.doThrow(new IllegalStateException("数据库失败"))
                .when(repository).register(any(), any());
        var service = new GovernedFileDataSourceRegistrationService(repository, staging, storage,
                () -> 230000000000000001L, java.time.Clock.systemUTC());

        assertThrows(IllegalStateException.class, () -> service.register(new RegisterFileDataSourceCommand(
                context(true), "210000000000000001", "文件来源", new ByteArrayInputStream(new byte[] {1}))));

        verify(storage).delete(stored);
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(new com.aiplatform.foundation.shared.kernel.tenant.TenantId(100000000000000001L),
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new com.aiplatform.foundation.security.identity.MembershipId(3382738893456539650L),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE, 1,
                writable ? Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:write")) : Set.of(),
                "trc-file-register");
    }
}
