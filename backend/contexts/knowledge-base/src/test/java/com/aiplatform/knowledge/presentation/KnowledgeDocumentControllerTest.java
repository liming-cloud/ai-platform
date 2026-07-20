package com.aiplatform.knowledge.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.knowledge.application.KnowledgeDocumentApplicationService;
import com.aiplatform.knowledge.application.KnowledgeDocumentIngestApplicationService;
import com.aiplatform.knowledge.application.command.ParseKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.command.UploadKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.command.CancelKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.result.KnowledgeDocumentListItemResult;
import com.aiplatform.knowledge.application.result.ParseKnowledgeDocumentResult;
import com.aiplatform.knowledge.application.result.UploadKnowledgeDocumentResult;
import com.aiplatform.knowledge.presentation.controller.KnowledgeDocumentController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.web.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeDocumentControllerTest {
    private final KnowledgeDocumentApplicationService service = mock(KnowledgeDocumentApplicationService.class);
    private final KnowledgeDocumentIngestApplicationService ingestService = mock(KnowledgeDocumentIngestApplicationService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new KnowledgeDocumentController(service, ingestService, allowAllFeatures())
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        TraceContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void uploadsDocumentFromServerTenantContextAndAuthenticatedPrincipal() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DOC-001");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        byte[] content = "制度内容".getBytes(StandardCharsets.UTF_8);
        when(service.uploadDocument(any(UploadKnowledgeDocumentCommand.class)))
                .thenReturn(new UploadKnowledgeDocumentResult("8001", "8101", "UPLOADED"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "员工手册.pdf",
                "application/pdf",
                content
        );

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "7001")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-DOC-001"))
                .andExpect(jsonPath("$.body.documentId").value("8001"))
                .andExpect(jsonPath("$.body.ingestJobId").value("8101"))
                .andExpect(jsonPath("$.body.status").value("UPLOADED"));

        ArgumentCaptor<UploadKnowledgeDocumentCommand> commandCaptor =
                ArgumentCaptor.forClass(UploadKnowledgeDocumentCommand.class);
        verify(service).uploadDocument(commandCaptor.capture());
        UploadKnowledgeDocumentCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.knowledgeBaseId()).isEqualTo("7001");
        assertThat(command.fileName()).isEqualTo("员工手册.pdf");
        assertThat(command.contentType()).isEqualTo("application/pdf");
        assertThat(command.content()).containsExactly(content);
        verifyNoInteractions(ingestService);
    }

    @Test
    void listsDocumentsFromServerTenantContext() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DOC-LIST-001");
        when(service.listDocuments(TenantId.of("2001"), "7001")).thenReturn(List.of(new KnowledgeDocumentListItemResult(
                "7001",
                "8001",
                "员工手册.pdf",
                "FAILED",
                "8101",
                "DOCUMENT_PARSE",
                "FAILED",
                "AIP-KB-PARSE-FAILED",
                "解析服务暂时不可用",
                "TRACE-KB-JOB-001",
                true
        )));

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-DOC-LIST-001"))
                .andExpect(jsonPath("$.body[0].knowledgeBaseId").value("7001"))
                .andExpect(jsonPath("$.body[0].documentId").value("8001"))
                .andExpect(jsonPath("$.body[0].fileName").value("员工手册.pdf"))
                .andExpect(jsonPath("$.body[0].status").value("FAILED"))
                .andExpect(jsonPath("$.body[0].jobId").value("8101"))
                .andExpect(jsonPath("$.body[0].jobType").value("DOCUMENT_PARSE"))
                .andExpect(jsonPath("$.body[0].jobStatus").value("FAILED"))
                .andExpect(jsonPath("$.body[0].failureCode").value("AIP-KB-PARSE-FAILED"))
                .andExpect(jsonPath("$.body[0].failureSummary").value("解析服务暂时不可用"))
                .andExpect(jsonPath("$.body[0].traceId").value("TRACE-KB-JOB-001"))
                .andExpect(jsonPath("$.body[0].manualRetryEligible").value(true));

        verify(service).listDocuments(TenantId.of("2001"), "7001");
        verifyNoInteractions(ingestService);
    }

    @Test
    void removesDocumentFromServerTenantContextAndAuthenticatedPrincipal() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DOC-REMOVE-001");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        when(service.removeDocument(TenantId.of("2001"), "7001", "8001", "1001"))
                .thenReturn(new KnowledgeDocumentListItemResult(
                        "7001", "8001", "员工手册.pdf", "UPLOADED",
                        null, null, null, null, null, null, false
                ));

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "7001", "8001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-DOC-REMOVE-001"))
                .andExpect(jsonPath("$.body.documentId").value("8001"))
                .andExpect(jsonPath("$.body.fileName").value("员工手册.pdf"));

        verify(service).removeDocument(TenantId.of("2001"), "7001", "8001", "1001");
        verifyNoInteractions(ingestService);
    }

    @Test
    void cancelsDocumentFromServerContextsWithValidatedReasonAndTrace() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-CANCEL-001");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        when(service.cancelDocument(any(CancelKnowledgeDocumentCommand.class)))
                .thenReturn(new KnowledgeDocumentListItemResult(
                        "7001", "8001", "员工手册.pdf", "CANCELLED",
                        "8101", "DOCUMENT_PARSE", "CANCELLED", null, null, "TRACE-KB-JOB-001", false,
                        "来源文件错误", "1001", "TRACE-KB-CANCEL-001",
                        java.time.Instant.parse("2026-07-19T08:10:00Z"), false
                ));

        mockMvc.perform(post(
                        "/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/cancel", "7001", "8001"
                ).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"来源文件错误\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.status").value("CANCELLED"))
                .andExpect(jsonPath("$.body.jobStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.body.cancellationReason").value("来源文件错误"))
                .andExpect(jsonPath("$.body.cancellationTraceId").value("TRACE-KB-CANCEL-001"))
                .andExpect(jsonPath("$.body.cancelEligible").value(false));

        ArgumentCaptor<CancelKnowledgeDocumentCommand> commandCaptor =
                ArgumentCaptor.forClass(CancelKnowledgeDocumentCommand.class);
        verify(service).cancelDocument(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(commandCaptor.getValue().operatorId()).isEqualTo("1001");
        assertThat(commandCaptor.getValue().reason()).isEqualTo("来源文件错误");
        assertThat(commandCaptor.getValue().traceId()).isEqualTo("TRACE-KB-CANCEL-001");
    }

    @Test
    void rejectsCancellationWithoutUsableReasonBeforeApplicationService() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-CANCEL-002");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));

        mockMvc.perform(post(
                        "/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/cancel", "7001", "8001"
                ).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service, ingestService);
    }

    @Test
    void rejectsUploadWithoutOriginalFilename() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DOC-003");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                "text/plain",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "7001")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.header.code").value("AIP-KB-UPLOADINVALID"))
                .andExpect(jsonPath("$.header.message").value("上传文件名不能为空"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-DOC-003"));

        verifyNoInteractions(service, ingestService);
    }

    @Test
    void rejectsUploadWithoutContentType() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DOC-004");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "员工手册.txt",
                null,
                "制度内容".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "7001")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.header.code").value("AIP-KB-UPLOADINVALID"))
                .andExpect(jsonPath("$.header.message").value("上传文件 MIME 类型不能为空"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-DOC-004"));

        verifyNoInteractions(service, ingestService);
    }

    @Test
    void parsesDocumentFromServerTenantContextAndAuthenticatedPrincipal() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-KB-DOC-002");
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        when(ingestService.parseDocument(any(ParseKnowledgeDocumentCommand.class)))
                .thenReturn(new ParseKnowledgeDocumentResult("8001", "8101", "CHUNKED", 1));

        mockMvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/parse", "7001", "8001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-KB-DOC-002"))
                .andExpect(jsonPath("$.body.documentId").value("8001"))
                .andExpect(jsonPath("$.body.ingestJobId").value("8101"))
                .andExpect(jsonPath("$.body.status").value("CHUNKED"))
                .andExpect(jsonPath("$.body.chunkCount").value(1));

        ArgumentCaptor<ParseKnowledgeDocumentCommand> commandCaptor =
                ArgumentCaptor.forClass(ParseKnowledgeDocumentCommand.class);
        verify(ingestService).parseDocument(commandCaptor.capture());
        ParseKnowledgeDocumentCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.knowledgeBaseId()).isEqualTo("7001");
        assertThat(command.documentId()).isEqualTo("8001");
        verifyNoInteractions(service);
    }

    private static TenantFeatureGuard allowAllFeatures() {
        return new TenantFeatureGuard((tenantId, featureCode) -> true);
    }
}
