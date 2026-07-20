package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.CreateKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.command.DisableKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.command.UpdateKnowledgeBaseCommand;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseListItemProjection;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeBaseApplicationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-22T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void createKnowledgeBasePersistsTenantScopedActiveKnowledgeBase() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        ModelUsagePolicyPort modelUsagePolicyPort = mock(ModelUsagePolicyPort.class);
        RbacAuthorizationQueryPort authorizationQueryPort = mockAuthorization();
        when(repository.nextKnowledgeBaseId()).thenReturn("7001");
        when(repository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelUsagePolicyPort.decide(any(KnowledgeModelUsageRequest.class))).thenReturn(allowEmbeddingDecision());
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                repository,
                modelUsagePolicyPort,
                authorizationQueryPort,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        var result = service.createKnowledgeBase(new CreateKnowledgeBaseCommand(
                TenantId.of("2001"),
                " 1001 ",
                " user ",
                " 企业制度知识库 ",
                " public ",
                " 用于制度问答 ",
                " 17 "
        ));

        assertThat(result.knowledgeBaseId()).isEqualTo("7001");
        assertThat(result.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<KnowledgeBase> savedCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(savedCaptor.capture());
        KnowledgeBase saved = savedCaptor.getValue();
        assertThat(saved.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(saved.knowledgeBaseId()).isEqualTo("7001");
        assertThat(saved.name()).isEqualTo("企业制度知识库");
        assertThat(saved.description()).isEqualTo("用于制度问答");
        assertThat(saved.visibility()).isEqualTo(KnowledgeVisibility.PUBLIC);
        assertThat(saved.createdBy()).isEqualTo("1001");
        assertThat(saved.allowedPrincipalIds()).containsExactly("1001");
        assertThat(saved.published()).isTrue();
        assertThat(saved.embeddingModelConfigId()).isEqualTo("17");
        assertThat(saved.createdAt()).isEqualTo(Instant.parse("2026-05-22T08:00:00Z"));

        ArgumentCaptor<KnowledgeModelUsageRequest> modelRequestCaptor = ArgumentCaptor.forClass(KnowledgeModelUsageRequest.class);
        verify(modelUsagePolicyPort).decide(modelRequestCaptor.capture());
        KnowledgeModelUsageRequest modelRequest = modelRequestCaptor.getValue();
        assertThat(modelRequest.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(modelRequest.actorType()).isEqualTo("user");
        assertThat(modelRequest.knowledgeBaseId()).isEqualTo("7001");
        assertThat(modelRequest.useCase()).isEqualTo(KnowledgeModelUseCase.RAG_INDEXING);
        assertThat(modelRequest.requiredCapability()).isEqualTo(KnowledgeModelCapability.EMBEDDING);
        assertThat(modelRequest.requestedModelConfigId()).isEqualTo("17");
    }

    @Test
    void createKnowledgeBaseRejectsDeniedEmbeddingModelBeforeSaving() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        ModelUsagePolicyPort modelUsagePolicyPort = mock(ModelUsagePolicyPort.class);
        RbacAuthorizationQueryPort authorizationQueryPort = mockAuthorization();
        when(repository.nextKnowledgeBaseId()).thenReturn("7001");
        when(modelUsagePolicyPort.decide(any(KnowledgeModelUsageRequest.class)))
                .thenReturn(KnowledgeModelUsageDecision.deny("model_policy_projection_missing", "模型使用策略投影不存在或未同步"));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                repository,
                modelUsagePolicyPort,
                authorizationQueryPort,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createKnowledgeBase(new CreateKnowledgeBaseCommand(
                        TenantId.of("2001"),
                        "1001",
                        "user",
                        "企业制度知识库",
                        "public",
                        "用于制度问答",
                        "17"
                )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("模型使用策略投影不存在或未同步");
        verify(repository, never()).save(any(KnowledgeBase.class));
    }

    @Test
    void createKnowledgeBaseRejectsUnsupportedVisibilityBeforeGeneratingId() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                repository,
                mock(ModelUsagePolicyPort.class),
                mock(RbacAuthorizationQueryPort.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createKnowledgeBase(new CreateKnowledgeBaseCommand(
                        TenantId.of("2001"),
                        "1001",
                        "user",
                        "企业制度知识库",
                        "internal",
                        "用于制度问答",
                        "17"
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的知识库可见性：internal");
        verify(repository, never()).nextKnowledgeBaseId();
        verify(repository, never()).save(any(KnowledgeBase.class));
    }

    @Test
    void createKnowledgeBaseCommandRejectsBlankNameWithChineseMessage() {
        assertThatThrownBy(() -> new CreateKnowledgeBaseCommand(
                        TenantId.of("2001"),
                        "1001",
                        "user",
                        " ",
                        "public",
                        "用于制度问答",
                        "17"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库名称不能为空");
    }

    @Test
    void listKnowledgeBasesReturnsTenantScopedManagementItems() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findListItemsByTenant(TenantId.of("2001"))).thenReturn(List.of(new KnowledgeBaseListItemProjection(
                "7001",
                "企业制度知识库",
                "public",
                "ACTIVE",
                "17"
        )));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                repository,
                mock(ModelUsagePolicyPort.class),
                mock(RbacAuthorizationQueryPort.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        var results = service.listKnowledgeBases(TenantId.of("2001"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).knowledgeBaseId()).isEqualTo("7001");
        assertThat(results.get(0).name()).isEqualTo("企业制度知识库");
        assertThat(results.get(0).status()).isEqualTo("ACTIVE");
        assertThat(results.get(0).embeddingModelConfigId()).isEqualTo("17");
        verify(repository).findListItemsByTenant(TenantId.of("2001"));
    }

    @Test
    void updateKnowledgeBasePersistsSettingsAndPublishesAudit() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        ModelUsagePolicyPort modelUsagePolicyPort = mock(ModelUsagePolicyPort.class);
        RbacAuthorizationQueryPort authorizationQueryPort = mockAuthorization();
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        KnowledgeBase existing = activeKnowledgeBase();
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                repository,
                modelUsagePolicyPort,
                authorizationQueryPort,
                auditPublisher,
                FIXED_CLOCK
        );

        var result = service.updateKnowledgeBase(new UpdateKnowledgeBaseCommand(
                TenantId.of("2001"),
                "1001",
                "user",
                "7001",
                "企业制度知识库 v2",
                "department",
                "用于制度和流程问答",
                "17"
        ));

        assertThat(result.name()).isEqualTo("企业制度知识库 v2");
        assertThat(result.description()).isEqualTo("用于制度和流程问答");
        assertThat(result.visibility()).isEqualTo("department");
        ArgumentCaptor<KnowledgeBase> savedCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().visibility()).isEqualTo(KnowledgeVisibility.DEPARTMENT);
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("KNOWLEDGE_BASE_UPDATE");
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("7001");
    }

    @Test
    void disableKnowledgeBaseMarksItUnavailableAndPublishesHighRiskAudit() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(java.util.Optional.of(activeKnowledgeBase()));
        when(repository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
                repository,
                mock(ModelUsagePolicyPort.class),
                mock(RbacAuthorizationQueryPort.class),
                auditPublisher,
                FIXED_CLOCK
        );

        var result = service.disableKnowledgeBase(new DisableKnowledgeBaseCommand(TenantId.of("2001"), "1001", "7001"));

        assertThat(result.status()).isEqualTo("DISABLED");
        ArgumentCaptor<KnowledgeBase> savedCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().published()).isFalse();
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("KNOWLEDGE_BASE_DISABLE");
        assertThat(auditCaptor.getValue().riskLevel()).isEqualTo("HIGH");
    }

    private static RbacAuthorizationQueryPort mockAuthorization() {
        RbacAuthorizationQueryPort authorizationQueryPort = mock(RbacAuthorizationQueryPort.class);
        when(authorizationQueryPort.findRoleCodes(TenantId.of("2001"), "user", "1001")).thenReturn(Set.of("platform-admin"));
        when(authorizationQueryPort.findPermissionCodes(TenantId.of("2001"), "user", "1001"))
                .thenReturn(Set.of(new PermissionCode("knowledge-base:knowledge-base:create")));
        return authorizationQueryPort;
    }

    private static KnowledgeModelUsageDecision allowEmbeddingDecision() {
        return KnowledgeModelUsageDecision.allow(new KnowledgeModelRuntimeDescriptor(
                "17",
                "3001",
                "openai-compatible",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef("https://models.example/v1", "api_key", "secret-17", "sk-***"),
                "text-embedding-3-large",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Map.of("dimensions", 1536),
                "a".repeat(64),
                "policy-v1"
        ), Map.of("decision", "allowed"));
    }

    private static KnowledgeBase activeKnowledgeBase() {
        return new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                "17",
                true,
                true,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z")
        );
    }
}
