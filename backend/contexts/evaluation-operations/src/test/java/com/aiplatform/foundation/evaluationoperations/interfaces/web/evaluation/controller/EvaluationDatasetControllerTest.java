package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationRequest;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationDatasetRegistrationService;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;

class EvaluationDatasetControllerTest {

    @Test
    void 登记入口只能转发正文和保留策略且绝不投影私有对象位置() throws Exception {
        var registrations = mock(EvaluationDatasetRegistrationService.class);
        var controller = new EvaluationDatasetController(registrations);
        var content = new MockMultipartFile(
                "content",
                "dataset.jsonl",
                "application/x-ndjson",
                "{\"input\":\"问题\",\"expectedEvidenceStableIds\":[\"evidence-1\"],\"expectedOutcome\":\"ANSWER\"}\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var context = mock(TrustedRequestContext.class);
        when(registrations.register(any(), any())).thenReturn(dataset());

        var response = controller.register(context, content, "retention-30d");

        var request = ArgumentCaptor.forClass(EvaluationDatasetRegistrationRequest.class);
        verify(registrations).register(org.mockito.Mockito.same(context), request.capture());
        assertThat(request.getValue().retentionPolicyId()).isEqualTo("retention-30d");
        assertThat(request.getValue().content().readAllBytes()).isEqualTo(content.getBytes());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody())
                .extracting(
                        EvaluationDatasetRegistrationView::id,
                        EvaluationDatasetRegistrationView::contentDigest,
                        EvaluationDatasetRegistrationView::entryCount,
                        EvaluationDatasetRegistrationView::status)
                .containsExactly("9001", "a".repeat(64), 1, "PUBLISHED");
    }

    @Test
    void HTTP入口不绑定身份摘要条目数或私有对象位置() throws Exception {
        var registrations = mock(EvaluationDatasetRegistrationService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new EvaluationDatasetController(registrations);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(registrations.register(any(), any())).thenReturn(dataset());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/evaluation-datasets")
                        .file(new MockMultipartFile(
                                "content",
                                "dataset.jsonl",
                                "application/x-ndjson",
                                "{\"input\":\"问题\",\"expectedEvidenceStableIds\":[\"evidence-1\"],\"expectedOutcome\":\"ANSWER\"}\n"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .param("retentionPolicyId", "retention-30d"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.id").value("9001"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.privateContentReference").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.content").doesNotExist());

        verify(registrations).register(org.mockito.Mockito.same(context), any());
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

    private static EvaluationDataset dataset() {
        return EvaluationDataset.create(
                        new TenantId(101),
                        new EvaluationDatasetId(9001L),
                        "private://evaluation/101/9001/content",
                        "a".repeat(64),
                        1,
                        EvaluationDatasetContentClassification.SYNTHETIC,
                        "retention-30d",
                        Instant.parse("2026-08-05T06:00:00Z"))
                .publish();
    }
}
