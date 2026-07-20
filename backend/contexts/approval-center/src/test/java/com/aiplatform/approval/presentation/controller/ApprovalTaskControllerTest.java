package com.aiplatform.approval.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.approval.application.ApprovalTaskApplicationService;
import com.aiplatform.approval.application.result.ApprovalTaskPageResult;
import com.aiplatform.approval.application.result.ApprovalTaskResult;
import com.aiplatform.approval.presentation.converter.ApprovalPresentationConverter;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApprovalTaskControllerTest {
    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void listsProviderAndConfigCreationEvidenceContainingExplicitNullFacts() throws Exception {
        ApprovalTaskApplicationService service = mock(ApprovalTaskApplicationService.class);
        Map<String, Object> providerEvidence = new LinkedHashMap<>();
        providerEvidence.put("before", null);
        providerEvidence.put("modelConfigId", null);
        providerEvidence.put("after", Map.of("providerName", "Enterprise provider"));
        Map<String, Object> configEvidence = new LinkedHashMap<>();
        configEvidence.put("before", null);
        configEvidence.put("after", Map.of("modelName", "GPT 4.1 Mini"));
        when(service.listTasks(any())).thenReturn(new ApprovalTaskPageResult(
                List.of(
                        result("7001", "model_provider", "3001", providerEvidence),
                        result("7002", "model_config", "4001", configEvidence)
                ),
                2,
                1,
                20,
                1
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApprovalTaskController(
                service,
                new ApprovalPresentationConverter()
        )).build();
        TenantContext.set(TenantId.of("2001"));

        mvc.perform(get("/api/v1/approvals/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.items[0].evidence.before").value(nullValue()))
                .andExpect(jsonPath("$.body.items[0].evidence.modelConfigId").value(nullValue()))
                .andExpect(jsonPath("$.body.items[0].evidence.after.providerName")
                        .value("Enterprise provider"))
                .andExpect(jsonPath("$.body.items[1].evidence.before").value(nullValue()))
                .andExpect(jsonPath("$.body.items[1].evidence.after.modelName").value("GPT 4.1 Mini"))
                .andExpect(jsonPath("$.body.items[0].evidenceDigest").value("a".repeat(64)))
                .andExpect(jsonPath("$.body.items[1].targetDigest").value("b".repeat(64)));
    }

    @Test
    void getsOneAuthoritativeTaskForUncertainDecisionReconciliation() throws Exception {
        ApprovalTaskApplicationService service = mock(ApprovalTaskApplicationService.class);
        when(service.getTask(TenantId.of("2001"), "7001"))
                .thenReturn(result("7001", "model_provider", "3001", Map.of("before", "snapshot")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApprovalTaskController(
                service,
                new ApprovalPresentationConverter()
        )).build();
        TenantContext.set(TenantId.of("2001"));

        mvc.perform(get("/api/v1/approvals/tasks/7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.taskId").value("7001"))
                .andExpect(jsonPath("$.body.approvalRequestId").value("REQ-7001"))
                .andExpect(jsonPath("$.body.evidenceDigest").value("a".repeat(64)));
    }

    private static ApprovalTaskResult result(
            String taskId,
            String resourceType,
            String resourceId,
            Map<String, Object> evidence
    ) {
        return new ApprovalTaskResult(
                taskId,
                "REQ-" + taskId,
                "model_config_change",
                "model-hub",
                resourceType,
                resourceId,
                "模型审批 " + resourceId,
                "HIGH_RISK",
                "1001",
                null,
                "PENDING",
                "TRACE-" + taskId,
                "model-approval-evidence/v1",
                "a".repeat(64),
                "b".repeat(64),
                evidence
        );
    }
}
