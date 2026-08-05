package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationRunExecutionRequest;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationRunExecutionService;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetSnapshot;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation.EvaluationObservation;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.policy.QualityGatePolicy;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.service.EvaluationExecution;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class EvaluationRunControllerTest {

    @Test
    void 运行入口只能转发目标与数据集标识并返回无正文终态投影() {
        var executions = mock(EvaluationRunExecutionService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new EvaluationRunController(executions);
        when(executions.execute(any(), any())).thenReturn(completedRun());

        var response = controller.execute(context, new EvaluationRunExecuteRequest(9001L, 9101L));

        var request = ArgumentCaptor.forClass(EvaluationRunExecutionRequest.class);
        verify(executions).execute(org.mockito.Mockito.same(context), request.capture());
        assertThat(request.getValue())
                .extracting(EvaluationRunExecutionRequest::datasetId, EvaluationRunExecutionRequest::assistantVersionId)
                .containsExactly(9001L, 9101L);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody())
                .extracting(
                        EvaluationRunView::id,
                        EvaluationRunView::assistantVersionId,
                        EvaluationRunView::datasetId,
                        EvaluationRunView::status,
                        EvaluationRunView::expectedEvidenceCount,
                        EvaluationRunView::recallAt10)
                .containsExactly("9201", "9101", "9001", "PASSED", 1, 1D);
        assertThat(response.getBody().toString())
                .doesNotContain("private://", "输入", "回答", "路由摘要", "input", "answer");
    }

    @Test
    void HTTP不绑定租户身份正文或私有对象位置() throws Exception {
        var executions = mock(EvaluationRunExecutionService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new EvaluationRunController(executions);
        when(executions.execute(any(), any())).thenReturn(completedRun());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/evaluation-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetId":9001,"assistantVersionId":9101}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value("9201"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("PASSED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.privateContentReference").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.input").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.answer").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.modelRouteFingerprint").doesNotExist());

        verify(executions).execute(org.mockito.Mockito.same(context), any());
    }

    private static HandlerMethodArgumentResolver trustedContext(TrustedRequestContext context) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(TrustedRequestContext.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer container,
                    NativeWebRequest request,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return context;
            }
        };
    }

    private static EvaluationRun completedRun() {
        var dataset = EvaluationDataset.create(
                        new TenantId(8001L),
                        new EvaluationDatasetId(9001L),
                        "private://evaluation/8001/9001/content",
                        "a".repeat(64),
                        2,
                        EvaluationDatasetContentClassification.SYNTHETIC,
                        "retention-30d",
                        Instant.parse("2026-08-05T08:00:00Z"))
                .publish();
        var run = EvaluationRun.start(
                new TenantId(8001L),
                new EvaluationRunId(9201L),
                new EvaluationTarget(9101L, 7L),
                EvaluationDatasetSnapshot.from(dataset),
                QualityGatePolicy.qualityGateV1().snapshot(),
                "b".repeat(64),
                "evaluation-metrics-v1",
                Instant.parse("2026-08-05T08:01:00Z"));
        return EvaluationExecution.complete(
                run,
                dataset,
                new EvaluationObservation(new TenantId(8001L), 1, 1, 1, 1, 1, 1, 1, 0, false),
                Instant.parse("2026-08-05T08:02:00Z")).run();
    }
}
