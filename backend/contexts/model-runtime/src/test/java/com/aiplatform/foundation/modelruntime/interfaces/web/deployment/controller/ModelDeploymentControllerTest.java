package com.aiplatform.foundation.modelruntime.interfaces.web.deployment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.deployment.command.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.command.DisableModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.query.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailabilityNextStep;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.deployment.service.ModelDeploymentService;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ModelDeploymentControllerTest {

    @Test
    void 探测HTTP入口只从可信上下文取身份且不接收范围参数() throws Exception {
        var deployments = mock(ModelDeploymentService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ModelDeploymentController(deployments);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(deployments.probe(any())).thenReturn(probeResult());

        mvc.perform(post("/api/v1/model-deployments/320000000000000001/probe")
                        .param("tenantId", "100000000000000099")
                        .param("subjectId", "4921825928971026434")
                        .contentType("application/json")
                        .content("{" + "\"expectedVersion\":0,\"timeoutMillis\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.modelDeployment.status").value("VERIFIED"))
                .andExpect(jsonPath("$.modelDeployment.availability").value("HEALTHY"))
                .andExpect(jsonPath("$.modelDeployment.nextStep").value("PUBLISH"))
                .andExpect(jsonPath("$.modelDeployment.verificationExpiresAt")
                        .value("2026-08-11T02:00:00Z"));

        var command = ArgumentCaptor.forClass(ProbeModelDeploymentCommand.class);
        verify(deployments).probe(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(
                "320000000000000001", command.getValue().modelDeploymentId());
        org.junit.jupiter.api.Assertions.assertEquals(0, command.getValue().expectedVersion());
        org.junit.jupiter.api.Assertions.assertEquals(Duration.ofSeconds(2), command.getValue().timeout());
    }

    @Test
    void 列表HTTP投影当前部署可用性且只使用可信上下文() throws Exception {
        var deployments = mock(ModelDeploymentService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ModelDeploymentController(deployments);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(deployments.list(any())).thenReturn(PageResult.of(
                List.of(probeResult().modelDeployment()),
                new PageQuery(2, 10),
                11));

        mvc.perform(get("/api/v1/model-deployments")
                        .param("pageNumber", "2")
                        .param("pageSize", "10")
                        .param("tenantId", "100000000000000099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].availability").value("HEALTHY"))
                .andExpect(jsonPath("$.items[0].nextStep").value("PUBLISH"))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.totalElements").value(11));

        var query = ArgumentCaptor.forClass(ListModelDeploymentsQuery.class);
        verify(deployments).list(query.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, query.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(new PageQuery(2, 10), query.getValue().page());
    }

    @Test
    void 停用HTTP入口只从可信上下文取身份且不接收范围参数() throws Exception {
        var deployments = mock(ModelDeploymentService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ModelDeploymentController(deployments);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(deployments.disable(any())).thenReturn(disabledView());

        mvc.perform(post("/api/v1/model-deployments/320000000000000001/disable")
                        .param("tenantId", "100000000000000099")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.nextStep").value("NONE"));

        var command = ArgumentCaptor.forClass(DisableModelDeploymentCommand.class);
        verify(deployments).disable(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(
                "320000000000000001", command.getValue().modelDeploymentId());
        org.junit.jupiter.api.Assertions.assertEquals(2, command.getValue().expectedVersion());
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

    private static ProbeModelDeploymentResult probeResult() {
        var view = new ModelDeploymentView(
                "320000000000000001",
                "310000000000000001",
                1,
                "deterministic-chat",
                "演示对话模型",
                "CHAT",
                Set.of(ModelCapability.CHAT),
                8192,
                1024,
                null,
                Instant.parse("2026-08-11T02:00:00Z"),
                "VERIFIED",
                ModelDeploymentAvailability.HEALTHY,
                ModelDeploymentAvailabilityNextStep.PUBLISH,
                1,
                Instant.parse("2026-08-10T02:00:00Z"));
        return new ProbeModelDeploymentResult(
                view,
                true,
                Set.of(ModelCapability.CHAT),
                Duration.ofMillis(10),
                "DETERMINISTIC_OK",
                Instant.parse("2026-08-10T02:00:10Z"));
    }

    private static ModelDeploymentView disabledView() {
        var verified = probeResult().modelDeployment();
        return new ModelDeploymentView(
                verified.id(),
                verified.providerConnectionId(),
                verified.providerConnectionRevision(),
                verified.modelCode(),
                verified.displayName(),
                verified.modelType(),
                verified.capabilities(),
                verified.contextWindow(),
                verified.outputLimit(),
                verified.embeddingDimension(),
                verified.verificationExpiresAt(),
                "DISABLED",
                ModelDeploymentAvailability.UNAVAILABLE,
                ModelDeploymentAvailabilityNextStep.NONE,
                3,
                verified.createdAt());
    }
}
