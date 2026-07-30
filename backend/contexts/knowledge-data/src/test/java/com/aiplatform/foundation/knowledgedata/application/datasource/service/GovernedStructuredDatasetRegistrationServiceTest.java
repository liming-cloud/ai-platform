package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterStructuredDatasetCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GovernedStructuredDatasetRegistrationServiceTest {

    @Test
    void 有知识写权限时平台保存私有CSV并原子登记结构化数据集修订() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var staging = mock(TextContentStagingPort.class);
        var storage = mock(StructuredDatasetCsvStoragePort.class);
        var staged = mock(StagedTextContent.class);
        when(staging.stage(any())).thenReturn(staged);
        when(storage.put(any())).thenReturn(csv());
        var service = new GovernedStructuredDatasetRegistrationService(
                repository, staging, storage, () -> 230000000000000001L,
                java.time.Clock.fixed(Instant.parse("2026-07-30T04:00:00Z"), java.time.ZoneOffset.UTC));

        var result = service.register(command(context(true)));

        assertEquals("DRAFT", result.status().name());
        verify(storage).put(any(StructuredDatasetCsvStorageRequest.class));
        verify(repository).register(any(), any());
    }

    @Test
    void 无知识写权限时不暂存不写CSV也不登记() {
        var repository = mock(GovernedDataSourceRepository.class);
        var staging = mock(TextContentStagingPort.class);
        var storage = mock(StructuredDatasetCsvStoragePort.class);
        var service = new GovernedStructuredDatasetRegistrationService(
                repository, staging, storage, () -> 230000000000000001L, java.time.Clock.systemUTC());

        assertThrows(KnowledgePermissionDeniedException.class, () -> service.register(command(context(false))));

        verifyNoInteractions(repository, staging, storage);
    }

    @Test
    void 登记持久化失败时删除刚写入的私有CSV() throws Exception {
        var repository = mock(GovernedDataSourceRepository.class);
        var staging = mock(TextContentStagingPort.class);
        var storage = mock(StructuredDatasetCsvStoragePort.class);
        var staged = mock(StagedTextContent.class);
        when(staging.stage(any())).thenReturn(staged);
        when(storage.put(any())).thenReturn(csv());
        org.mockito.Mockito.doThrow(new IllegalStateException("数据库失败"))
                .when(repository).register(any(), any());
        var service = new GovernedStructuredDatasetRegistrationService(
                repository, staging, storage, () -> 230000000000000001L, java.time.Clock.systemUTC());

        assertThrows(IllegalStateException.class, () -> service.register(command(context(true))));

        verify(storage).delete(csv());
    }

    private static RegisterStructuredDatasetCommand command(TrustedRequestContext context) {
        return new RegisterStructuredDatasetCommand(
                context,
                "210000000000000001",
                "客服知识库",
                "csv-mapping-v1",
                "content",
                "title",
                List.of(
                        new StructuredDatasetFieldMapping(
                                "content", StructuredDatasetLogicalType.TEXT,
                                StructuredDatasetSensitivityLevel.PUBLIC),
                        new StructuredDatasetFieldMapping(
                                "title", StructuredDatasetLogicalType.TEXT,
                                StructuredDatasetSensitivityLevel.INTERNAL)),
                StructuredDatasetRowFilter.NON_BLANK_CONTENT,
                new ByteArrayInputStream("title,content\nA,测试".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static StructuredDatasetCsvReference csv() {
        return new StructuredDatasetCsvReference(
                "foundation-v1/tenants/100000000000000001/knowledge-data/datasets/1/1/data.csv",
                "a".repeat(64),
                32);
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(
                new com.aiplatform.foundation.shared.kernel.tenant.TenantId(100000000000000001L),
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new com.aiplatform.foundation.security.identity.MembershipId(3382738893456539650L),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE,
                1,
                writable ? Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:write")) : Set.of(),
                "trc-dataset-register");
    }
}
