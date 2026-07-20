package com.aiplatform.knowledge.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.knowledge.application.command.RetrieveKnowledgeCommand;
import com.aiplatform.knowledge.application.retrieval.result.RetrieveKnowledgeResult;
import com.aiplatform.knowledge.application.retrieval.result.RetrievalAuthorizationSnapshot;
import com.aiplatform.knowledge.application.retrieval.service.KnowledgeRetrievalApplicationService;
import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import com.aiplatform.knowledge.presentation.retrieval.controller.KnowledgeRetrievalController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeRetrievalControllerTest {
    @AfterEach
    void cleanupContext() {
        TenantContext.clear();
        PrincipalContext.clear();
        TraceContext.clear();
    }

    @Test
    void searchUsesServerContextAndReturnsApplicationResult() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("trace-retrieval");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        KnowledgeRetrievalApplicationService service = mock(KnowledgeRetrievalApplicationService.class);
        when(service.retrieve(any(RetrieveKnowledgeCommand.class)))
                .thenReturn(new RetrieveKnowledgeResult(false, List.of(), "未召回到具备权限且可信的知识片段", null, null, null)
                        .withSecurityEvidence(
                                RetrievalAuthorizationSnapshot.of(
                                        List.of("business-user"),
                                        List.of("knowledge-base:retrieval:query"),
                                        List.of("7001"),
                                        List.of()
                                ),
                                1,
                                0
                        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new KnowledgeRetrievalController(service, allowAllFeatures())).build();

        mvc.perform(post("/api/v1/knowledge-retrieval/search")
                        .contentType("application/json")
                        .content("""
                                {
                                  "knowledgeBaseIds": ["7001"],
                                  "query": "员工制度"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("trace-retrieval"))
                .andExpect(jsonPath("$.body.answerable").value(false))
                .andExpect(jsonPath("$.body.noAnswerReason").value("未召回到具备权限且可信的知识片段"))
                .andExpect(jsonPath("$.body.authorizationSnapshot.roleCodes[0]").value("business-user"))
                .andExpect(jsonPath("$.body.authorizationSnapshot.requestedKnowledgeBaseIds[0]").value("7001"))
                .andExpect(jsonPath("$.body.authorizationSnapshot.authorizedKnowledgeBaseIds").isEmpty())
                .andExpect(jsonPath("$.body.filteredCount").value(0))
                .andExpect(jsonPath("$.body.filteredCitationCount").value(0));

        ArgumentCaptor<RetrieveKnowledgeCommand> commandCaptor =
                ArgumentCaptor.forClass(RetrieveKnowledgeCommand.class);
        verify(service).retrieve(commandCaptor.capture());
        RetrieveKnowledgeCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.principalId()).isEqualTo("1001");
        assertThat(command.principalType()).isEqualTo("user");
        assertThat(command.knowledgeBaseIds()).containsExactly("7001");
        assertThat(command.query()).isEqualTo("员工制度");
        assertThat(command.profile()).isEqualTo(RetrievalProfile.BALANCED);
        assertThat(command.answerModel()).isNull();
    }

    @Test
    void searchRejectsUnsupportedRetrievalProfileWithChineseMessage() {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        KnowledgeRetrievalApplicationService service = mock(KnowledgeRetrievalApplicationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new KnowledgeRetrievalController(service, allowAllFeatures())).build();

        assertThatThrownBy(() -> mvc.perform(post("/api/v1/knowledge-retrieval/search")
                        .contentType("application/json")
                        .content("""
                                {
                                  "knowledgeBaseIds": ["7001"],
                                  "query": "员工制度",
                                  "retrievalProfile": "hybrid"
                                }
                                """)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("不支持的检索画像：hybrid");
        verify(service, never()).retrieve(any(RetrieveKnowledgeCommand.class));
    }

    private static TenantFeatureGuard allowAllFeatures() {
        return new TenantFeatureGuard((tenantId, featureCode) -> true);
    }
}
