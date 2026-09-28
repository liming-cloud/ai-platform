package com.aiplatform.foundation.queryruntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.command.RunAssistantDraftTestCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunResult;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.service.AssistantTestRunService;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunStatus;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller.AssistantTestRunController;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.identity.TenantFeatureAvailability;
import com.aiplatform.foundation.security.satoken.web.SaTokenTrustedContextArgumentResolver;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssistantTestRunHttpTest {

    @Test
    void 草稿测试接口应从Bearer令牌恢复上下文并忽略伪造身份字段() throws Exception {
        var sessions = mock(AuthenticatedSessionGateway.class);
        var tests = mock(AssistantTestRunService.class);
        var features = mock(TenantFeatureAvailability.class);
        var tokenContext = tokenContext();
        when(sessions.resolve("signed-session-token")).thenReturn(tokenContext);
        when(tests.run(any())).thenReturn(new AssistantTestRunResult(
                "91", AssistantTestRunStatus.REFUSED, null, List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new AssistantTestRunController(tests))
                .setCustomArgumentResolvers(new SaTokenTrustedContextArgumentResolver(sessions, () -> 123L, features))
                .build();

        mvc.perform(post("/api/v1/assistant-versions/11/test-runs")
                        .header("Authorization", "Bearer signed-session-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"测试问题","tenantId":"100000000000000099","permissions":["platform:admin"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assistantTestRunId").value("91"))
                .andExpect(jsonPath("$.status").value("REFUSED"));

        var command = ArgumentCaptor.forClass(RunAssistantDraftTestCommand.class);
        verify(sessions).resolve("signed-session-token");
        verify(features).requireAvailable(tokenContext.tenantId(), "assistantDelivery");
        verify(tests).run(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(tokenContext.tenantId(), command.getValue().context().tenantId());
        org.junit.jupiter.api.Assertions.assertEquals(tokenContext.permissions(), command.getValue().context().permissions());
        org.junit.jupiter.api.Assertions.assertEquals("11", command.getValue().assistantVersionId());
        org.junit.jupiter.api.Assertions.assertEquals("测试问题", command.getValue().question());
    }

    private static TrustedRequestContext tokenContext() {
        return new TrustedRequestContext(
                new TenantId(700000000000000001L), new SubjectId(720000000000000001L),
                new MembershipId(730000000000000001L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 1, Set.of(new PermissionCode("knowledge:read")), "trc-session");
    }
}
