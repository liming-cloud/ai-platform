package com.aiplatform.audit.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.audit.application.AuditSecurityLogQueryService;
import com.aiplatform.audit.application.result.AuditSecurityLogPageResult;
import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import com.aiplatform.audit.presentation.response.AuditSecurityLogResponse;
import com.aiplatform.audit.presentation.response.AuditDetailDisclosurePolicy;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.shared.web.response.PageResponse;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditSecurityLogControllerTest {
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void exposesStablePageResponseWithoutAcceptingTenantFromTheRequest() {
        AuditSecurityLogQueryService service = mock(AuditSecurityLogQueryService.class);
        String subjectRef = "90323c3e-569a-4f13-ab3a-a5a115edc2b5";
        AuditSecurityLogResult row = new AuditSecurityLogResult(
                "118", "2001", "1002", "PERMISSION_REQUEST_DECIDED", "HIGH",
                "IAM_PERMISSION_REQUEST", null, subjectRef,
                "FAILED", "{}", "trace-001", Instant.parse("2026-06-24T08:00:00Z")
        );
        when(service.findLogs(
                TenantId.of("2001"), "1002", "permission_request_decided",
                "iam_permission_request", subjectRef, "trace-001",
                "high", "failed", "2026-06-24T00:00:00Z", "2026-06-25T00:00:00Z", 2, 20
        )).thenReturn(new AuditSecurityLogPageResult(List.of(row), 2, 20, 41));
        TenantContext.set(TenantId.of("2001"));
        AuditSecurityLogController controller = new AuditSecurityLogController(
                service,
                new AuditDetailDisclosurePolicy(new JsonMapper())
        );

        ApiResponse<PageResponse<AuditSecurityLogResponse>> response = controller.listLogs(
                "1002", "permission_request_decided", "iam_permission_request", subjectRef,
                "trace-001", "high", "failed",
                "2026-06-24T00:00:00Z", "2026-06-25T00:00:00Z", 2, 20
        );

        assertThat(response.body().items()).extracting(AuditSecurityLogResponse::id).containsExactly("118");
        assertThat(response.body().items().getFirst().subjectId()).isNull();
        assertThat(response.body().items().getFirst().subjectRef()).isEqualTo(subjectRef);
        assertThat(response.body().page()).isEqualTo(2);
        assertThat(response.body().pageSize()).isEqualTo(20);
        assertThat(response.body().total()).isEqualTo(41);
        assertThat(response.body().totalPages()).isEqualTo(3);
        verify(service).findLogs(
                TenantId.of("2001"), "1002", "permission_request_decided",
                "iam_permission_request", subjectRef, "trace-001",
                "high", "failed", "2026-06-24T00:00:00Z", "2026-06-25T00:00:00Z", 2, 20
        );
    }

    @Test
    void removesUnknownAndSensitiveDetailFieldsBeforeTheyReachTheClient() {
        AuditSecurityLogQueryService service = mock(AuditSecurityLogQueryService.class);
        AuditSecurityLogResult row = new AuditSecurityLogResult(
                "119", "2001", "1002", "LOGIN_FAILED", "HIGH", "IAM_USER", "3002",
                "3002",
                "FAILED",
                "{\"reason\":\"invalid credentials\",\"action\":\"LOGIN_FAILED\","
                        + "\"token\":\"bearer-secret\",\"password\":\"plain-secret\","
                        + "\"prompt\":\"private question\",\"unknown\":\"internal-only\"}",
                "trace-002", Instant.parse("2026-06-24T08:00:00Z")
        );
        when(service.findLogs(
                TenantId.of("2001"), null, null, null, null, null,
                null, null, null, null, 1, 20
        )).thenReturn(new AuditSecurityLogPageResult(List.of(row), 1, 20, 1));
        TenantContext.set(TenantId.of("2001"));
        AuditSecurityLogController controller = new AuditSecurityLogController(
                service,
                new AuditDetailDisclosurePolicy(new JsonMapper())
        );

        String disclosed = controller.listLogs(
                null, null, null, null, null, null, null, null, null, 1, 20
        ).body().items().getFirst().detailJson();

        assertThat(disclosed).contains("invalid credentials", "LOGIN_FAILED");
        assertThat(disclosed).doesNotContain(
                "bearer-secret", "plain-secret", "private question", "internal-only",
                "\"token\"", "\"password\"", "\"prompt\"", "\"unknown\""
        );
    }
}
