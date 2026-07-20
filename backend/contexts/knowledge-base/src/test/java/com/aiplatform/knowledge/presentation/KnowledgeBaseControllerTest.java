package com.aiplatform.knowledge.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.knowledge.application.KnowledgeBaseApplicationService;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseListItemProjection;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.presentation.controller.KnowledgeBaseController;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeBaseControllerTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-22T08:00:00Z"), ZoneOffset.UTC);

    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final ModelUsagePolicyPort modelUsagePolicyPort = mock(ModelUsagePolicyPort.class);
    private final RbacAuthorizationQueryPort authorizationQueryPort = mock(RbacAuthorizationQueryPort.class);
    private final KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(
            repository,
            modelUsagePolicyPort,
            authorizationQueryPort,
            mock(AuditEventPublisher.class),
            FIXED_CLOCK
    );
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new KnowledgeBaseController(service, allowAllFeatures())
    ).build();

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        TraceContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void createsKnowledgeBaseFromServerTenantContextAndAuthenticatedPrincipal() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-CREATE-001");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        when(repository.nextKnowledgeBaseId()).thenReturn("7001");
        when(repository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationQueryPort.findRoleCodes(TenantId.of("2001"), "user", "1001")).thenReturn(Set.of("platform-admin"));
        when(authorizationQueryPort.findPermissionCodes(TenantId.of("2001"), "user", "1001"))
                .thenReturn(Set.of(new PermissionCode("knowledge-base:knowledge-base:create")));
        when(modelUsagePolicyPort.decide(any(KnowledgeModelUsageRequest.class))).thenReturn(KnowledgeModelUsageDecision.allow(
                new KnowledgeModelRuntimeDescriptor(
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
                ),
                Map.of("decision", "allowed")
        ));

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "企业制度知识库",
                                  "visibility": "public",
                                  "description": "用于制度问答",
                                  "embeddingModelConfigId": "17"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-CREATE-001"))
                .andExpect(jsonPath("$.body.knowledgeBaseId").value("7001"))
                .andExpect(jsonPath("$.body.status").value("ACTIVE"));

        ArgumentCaptor<KnowledgeBase> knowledgeBaseCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(knowledgeBaseCaptor.capture());
        assertThat(knowledgeBaseCaptor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(knowledgeBaseCaptor.getValue().name()).isEqualTo("企业制度知识库");
        assertThat(knowledgeBaseCaptor.getValue().embeddingModelConfigId()).isEqualTo("17");
    }

    @Test
    void listsKnowledgeBasesFromServerTenantContext() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-LIST-001");
        when(repository.findListItemsByTenant(TenantId.of("2001"))).thenReturn(java.util.List.of(new KnowledgeBaseListItemProjection(
                "7001",
                "企业制度知识库",
                "public",
                "ACTIVE",
                "17"
        )));

        mockMvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-LIST-001"))
                .andExpect(jsonPath("$.body[0].knowledgeBaseId").value("7001"))
                .andExpect(jsonPath("$.body[0].name").value("企业制度知识库"))
                .andExpect(jsonPath("$.body[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.body[0].embeddingModelConfigId").value("17"));

        verify(repository).findListItemsByTenant(TenantId.of("2001"));
    }

    @Test
    void updatesKnowledgeBaseFromServerContextAndAuthenticatedPrincipal() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-UPDATE-001");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(java.util.Optional.of(activeKnowledgeBase()));
        when(repository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/v1/knowledge-bases/7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "企业制度知识库 v2",
                                  "visibility": "department",
                                  "description": "用于制度和流程问答",
                                  "embeddingModelConfigId": "17"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.body.knowledgeBaseId").value("7001"))
                .andExpect(jsonPath("$.body.name").value("企业制度知识库 v2"))
                .andExpect(jsonPath("$.body.visibility").value("department"));

        ArgumentCaptor<KnowledgeBase> knowledgeBaseCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(knowledgeBaseCaptor.capture());
        assertThat(knowledgeBaseCaptor.getValue().name()).isEqualTo("企业制度知识库 v2");
    }

    @Test
    void disablesKnowledgeBaseFromServerContextAndAuthenticatedPrincipal() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DISABLE-001");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(java.util.Optional.of(activeKnowledgeBase()));
        when(repository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/knowledge-bases/7001/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.body.knowledgeBaseId").value("7001"))
                .andExpect(jsonPath("$.body.status").value("DISABLED"));

        ArgumentCaptor<KnowledgeBase> knowledgeBaseCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(knowledgeBaseCaptor.capture());
        assertThat(knowledgeBaseCaptor.getValue().published()).isFalse();
    }

    private static TenantFeatureGuard allowAllFeatures() {
        return new TenantFeatureGuard((tenantId, featureCode) -> true);
    }

    private static KnowledgeBase activeKnowledgeBase() {
        return new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                com.aiplatform.knowledge.domain.KnowledgeVisibility.PUBLIC,
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
