package com.aiplatform.foundation.evaluationoperations.interfaces.web.release.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.evaluationoperations.application.release.service.ReleaseDecisionService;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunStatus;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationTarget;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecisionId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ReleaseDecisionControllerTest {

    @Test
    void 入口只转发路径中的运行标识并返回冻结决定投影() throws Exception {
        var decisions = mock(ReleaseDecisionService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ReleaseDecisionController(decisions);
        when(decisions.decide(any(), anyLong())).thenReturn(decision());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/evaluation-runs/9001/release-decisions"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value("9201"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.policyVersion").value("quality-gate-v1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.reasonCode").value("QUALITY_GATE_PASSED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.input").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.answer").doesNotExist());

        verify(decisions).decide(context, 9001L);
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

    private static ReleaseDecision decision() {
        var run = new EvaluationRunReleaseFact(
                new TenantId(8001L),
                new EvaluationRunId(9001L),
                new EvaluationTarget(9101L, 7L),
                "quality-gate-v1",
                EvaluationRunStatus.PASSED);
        return ReleaseDecision.decide(new ReleaseDecisionId(9201L), run, Instant.parse("2026-08-05T09:00:00Z"));
    }
}
