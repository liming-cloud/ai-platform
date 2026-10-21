package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DisablePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelDeploymentService;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailabilityNextStep;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PlatformModelDeploymentControllerTest {

    @Test
    void 停用只从平台会话恢复身份并返回安全部署摘要() throws Exception {
        var service = mock(PlatformModelDeploymentService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformModelDeploymentController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.disable(any())).thenReturn(view("DISABLED", 3));

        var continuity = mock(com.aiplatform.foundation.security.session.PlatformSessionContinuity.class);
        when(continuity.matches(context)).thenReturn(true);
        when(continuity.getAsBoolean()).thenReturn(true);
        mvc.perform(post("/api/v1/platform/model-supply/deployments/620000000000000001/disable")
                        .requestAttr(com.aiplatform.foundation.security.session.PlatformSessionContinuity.REQUEST_ATTRIBUTE, continuity)
                        .param("tenantId", "100000000000000099")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.providerConnectionId").value("610000000000000001"))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist());

        var command = ArgumentCaptor.forClass(DisablePlatformModelDeploymentCommand.class);
        verify(service).disable(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals("620000000000000001", command.getValue().modelDeploymentId());
        org.junit.jupiter.api.Assertions.assertEquals(2L, command.getValue().expectedVersion());
    }


    @Test
    void 正文不能伪造发布撤权或停用的服务端会话证明() throws Exception {
        var service = mock(PlatformModelDeploymentService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformModelDeploymentController(service))
                .setCustomArgumentResolvers(platformContext(context)).build();
        for (var suffix : new String[]{"publish", "disable", "grants/201/revoke"}) {
            var error = org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class,
                    () -> mvc.perform(post("/api/v1/platform/model-supply/deployments/620000000000000001/" + suffix)
                            .contentType("application/json")
                            .content("{\"expectedVersion\":2,\"expectedGrantVersion\":0,\"sessionActive\":true}")));
            org.junit.jupiter.api.Assertions.assertInstanceOf(com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class, error.getCause());
        }
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void 撤权只转发精确部署租户和两个修订并保留服务端证明() throws Exception {
        var service = mock(PlatformModelDeploymentService.class);
        var context = mock(PlatformRequestContext.class);
        var continuity = mock(com.aiplatform.foundation.security.session.PlatformSessionContinuity.class);
        when(continuity.matches(context)).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformModelDeploymentController(service))
                .setCustomArgumentResolvers(platformContext(context)).build();
        mvc.perform(post("/api/v1/platform/model-supply/deployments/620000000000000001/grants/201/revoke")
                        .requestAttr(com.aiplatform.foundation.security.session.PlatformSessionContinuity.REQUEST_ATTRIBUTE, continuity)
                        .contentType("application/json").content("{\"expectedVersion\":2,\"expectedGrantVersion\":0}"))
                .andExpect(status().isNoContent());
        verify(service).revokeSupply(context, "620000000000000001", 201L, 2L, 0L, continuity);
    }

    private static PlatformModelDeploymentView view(String status, long version) {
        return new PlatformModelDeploymentView("620000000000000001", "610000000000000001", 1,
                "deterministic-embedding", "本地嵌入", "EMBEDDING", Set.of(ModelCapability.EMBEDDING), 8192,
                null, 1024, Instant.parse("2026-08-13T03:00:00Z"), status,
                ModelDeploymentAvailability.UNAVAILABLE, ModelDeploymentAvailabilityNextStep.NONE, version,
                Instant.parse("2026-08-13T02:00:00Z"), "合成连接", null, null);
    }

    private static HandlerMethodArgumentResolver platformContext(PlatformRequestContext context) {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(PlatformRequestContext.class);
            }

            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return context;
            }
        };
    }
}
