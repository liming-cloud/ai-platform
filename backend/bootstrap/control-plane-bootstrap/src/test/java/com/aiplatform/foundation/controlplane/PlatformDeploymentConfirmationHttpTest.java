package com.aiplatform.foundation.controlplane;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelDeploymentService;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller.PlatformModelDeploymentController;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 验证真实控制器与全局错误映射，声明字符串不能被当成布尔确认后调用用例。 */
class PlatformDeploymentConfirmationHttpTest {
    @Test
    void 字符串数字或缺失不能冒充显式模型确认() throws Exception {
        for (var confirmation : new String[] { "\"true\"", "1", "false", "null" }) {
            var service = mock(PlatformModelDeploymentService.class);
            var context = mock(PlatformRequestContext.class);
            var mvc = MockMvcBuilders.standaloneSetup(new PlatformModelDeploymentController(service))
                    .setControllerAdvice(new com.aiplatform.foundation.platform.web.error.FoundationExceptionHandler(() -> 1001L))
                    .setCustomArgumentResolvers(platformContext(context)).build();
            mvc.perform(post("/api/v1/platform/model-supply/deployments").header("Idempotency-Key", "synthetic-confirmation-01")
                    .contentType("application/json").content("""
                    {"providerConnectionId":"11","expectedConnectionVersion":2,"modelCode":"synthetic-chat","displayName":"合成草稿",
                     "modelType":"CHAT","capabilities":["CHAT"],"contextWindow":128,"outputLimit":64,"embeddingDimension":null,
                     "rerankCandidateLimit":null,"parameterSource":"合成资料","plannedTenantIds":["21"],"modelConfirmed":%s}
                    """.formatted(confirmation)))
                    .andExpect(status().isBadRequest());
            org.mockito.Mockito.verifyNoInteractions(service);
        }
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
