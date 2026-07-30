package com.aiplatform.foundation.assistantdelivery.application.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CreatorId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
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

class DraftAssistantVersionLookupServiceTest {

    private static final TenantId TENANT = new TenantId(410000000000000001L);
    private static final TenantId OTHER_TENANT = new TenantId(410000000000000002L);
    private static final AssistantVersionId VERSION = new AssistantVersionId(420000000000000001L);

    @Test
    void 仅有助手写权限的同租户主体可以取得草稿执行快照() {
        var repository = mock(AssistantRepository.class);
        when(repository.findVersion(TENANT, VERSION)).thenReturn(Optional.of(draft()));

        var result = new DraftAssistantVersionLookupService(repository)
                .find(writer(), VERSION.value());

        assertTrue(result.isPresent());
        assertEquals(VERSION.value(), result.orElseThrow().assistantVersionId());
        assertTrue(result.orElseThrow().usesGovernedModelConfigurations());
    }

    @Test
    void 已发布版本和跨租户版本不得作为草稿测试快照返回() {
        var repository = mock(AssistantRepository.class);
        when(repository.findVersion(TENANT, VERSION)).thenReturn(Optional.of(draft().publish(Instant.now())));
        when(repository.findVersion(OTHER_TENANT, VERSION)).thenReturn(Optional.empty());
        var service = new DraftAssistantVersionLookupService(repository);

        assertFalse(service.find(writer(), VERSION.value()).isPresent());
        assertFalse(service.find(writer(OTHER_TENANT), VERSION.value()).isPresent());
    }

    @Test
    void 缺少助手写权限时关闭失败且不读取草稿() {
        var repository = mock(AssistantRepository.class);
        var service = new DraftAssistantVersionLookupService(repository);

        assertThrows(
                AssistantPermissionDeniedException.class,
                () -> service.find(reader(), VERSION.value()));
    }

    private static AssistantVersion draft() {
        return AssistantVersion.governedDraft(
                TENANT,
                VERSION,
                new AssistantId(430000000000000001L),
                1,
                new GovernedChatModelBinding(
                        440000000000000001L, 1, 450000000000000001L, 1, 512),
                460000000000000001L,
                470000000000000001L,
                1,
                new GovernedEmbeddingModelBinding(
                        480000000000000001L, 1, 490000000000000001L, 1, 1024),
                "只依据授权证据回答。",
                256,
                5,
                0.5,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE,
                new CreatorId(500000000000000001L),
                Instant.parse("2026-07-30T00:00:00Z"));
    }

    private static TrustedRequestContext writer() {
        return writer(TENANT);
    }

    private static TrustedRequestContext writer(TenantId tenantId) {
        return context(tenantId, Set.of(new PermissionCode("assistant:write")));
    }

    private static TrustedRequestContext reader() {
        return context(TENANT, Set.of(new PermissionCode("assistant:read")));
    }

    private static TrustedRequestContext context(TenantId tenantId, Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                tenantId,
                new SubjectId(510000000000000001L),
                new MembershipId(520000000000000001L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                permissions,
                "trace-draft-assistant");
    }
}
