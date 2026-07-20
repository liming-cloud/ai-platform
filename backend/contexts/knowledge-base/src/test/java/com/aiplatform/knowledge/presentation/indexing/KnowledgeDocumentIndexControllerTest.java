package com.aiplatform.knowledge.presentation.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.knowledge.application.command.IndexKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeDocumentIndexApplicationService;
import com.aiplatform.knowledge.application.result.IndexKnowledgeDocumentResult;
import com.aiplatform.knowledge.presentation.indexing.controller.KnowledgeDocumentIndexController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeDocumentIndexControllerTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");

    private final KnowledgeDocumentIndexApplicationService service = mock(KnowledgeDocumentIndexApplicationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new KnowledgeDocumentIndexController(service, allowAllFeatures())
    ).build();

    @AfterEach
    void cleanupContext() {
        TenantContext.clear();
        PrincipalContext.clear();
        TraceContext.clear();
    }

    @Test
    void indexUsesServerContextAndReturnsApplicationResult() throws Exception {
        TenantContext.set(TENANT_ID);
        TraceContext.ensureTraceId("trace-index");
        PrincipalContext.set(new AuthenticatedPrincipal(TENANT_ID, "user", "1001"));
        when(service.indexDocument(any(IndexKnowledgeDocumentCommand.class)))
                .thenReturn(new IndexKnowledgeDocumentResult(
                        "8001",
                        "8201",
                        "FAILED",
                        0,
                        "模型策略拒绝文档索引"
                ));

        mvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/index", "7001", "8001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("trace-index"))
                .andExpect(jsonPath("$.body.documentId").value("8001"))
                .andExpect(jsonPath("$.body.ingestJobId").value("8201"))
                .andExpect(jsonPath("$.body.status").value("FAILED"))
                .andExpect(jsonPath("$.body.indexedChunkCount").value(0))
                .andExpect(jsonPath("$.body.failureReason").value("模型策略拒绝文档索引"));

        ArgumentCaptor<IndexKnowledgeDocumentCommand> commandCaptor =
                ArgumentCaptor.forClass(IndexKnowledgeDocumentCommand.class);
        verify(service).indexDocument(commandCaptor.capture());
        IndexKnowledgeDocumentCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.operatorType()).isEqualTo("user");
        assertThat(command.knowledgeBaseId()).isEqualTo("7001");
        assertThat(command.documentId()).isEqualTo("8001");
    }

    private static TenantFeatureGuard allowAllFeatures() {
        return new TenantFeatureGuard((tenantId, featureCode) -> true);
    }
}
