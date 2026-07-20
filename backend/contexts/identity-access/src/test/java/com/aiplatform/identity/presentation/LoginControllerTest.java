package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.IdentitySessionApplicationService;
import com.aiplatform.identity.application.LoginApplicationService;
import com.aiplatform.identity.application.command.LoginCommand;
import com.aiplatform.identity.application.error.IdentityErrorCodes;
import com.aiplatform.identity.application.result.LoginResult;
import com.aiplatform.identity.presentation.controller.LoginController;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.web.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
    }

    @Test
    void loginUsesTenantCodeFromBodyAndTraceFromContext() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-LOGIN-001");
        LoginApplicationService loginService = mock(LoginApplicationService.class);
        when(loginService.login(any(LoginCommand.class))).thenReturn(new LoginResult(
                TenantId.of("2001"),
                "1001",
                "平台管理员",
                Set.of("platform_admin"),
                "token-1001",
                Instant.parse("2026-05-21T04:00:00Z")
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(
                loginService,
                mock(IdentitySessionApplicationService.class)
        )).build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantCode": "2001",
                                  "username": "admin",
                                  "password": "correct-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-LOGIN-001"))
                .andExpect(jsonPath("$.body.tenantId").value("2001"))
                .andExpect(jsonPath("$.body.userId").value("1001"))
                .andExpect(jsonPath("$.body.token").value("token-1001"));

        ArgumentCaptor<LoginCommand> commandCaptor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginService).login(commandCaptor.capture());
        LoginCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.loginName()).isEqualTo("admin");
        assertThat(command.rawPassword()).isEqualTo("correct-password");
        assertThat(command.traceId()).isEqualTo("TRACE-FILTER-LOGIN-001");
    }

    @Test
    void currentUserRejectsBlankBearerTokenBeforeApplicationService() {
        TenantContext.set(TenantId.of("2001"));
        TraceContext.ensureTraceId("TRACE-FILTER-CURRENT-001");
        IdentitySessionApplicationService sessionService = mock(IdentitySessionApplicationService.class);
        LoginController controller = new LoginController(mock(LoginApplicationService.class), sessionService);

        assertThatThrownBy(() -> controller.currentUser("Bearer   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authorization 请求头缺少访问令牌");
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginRateLimitUsesStableHttp429BusinessContract() throws Exception {
        LoginApplicationService loginService = mock(LoginApplicationService.class);
        when(loginService.login(any(LoginCommand.class))).thenThrow(new ApplicationException(
                IdentityErrorCodes.LOGIN_RATE_LIMITED,
                "登录失败已达限制"
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(
                        loginService,
                        mock(IdentitySessionApplicationService.class)
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantCode": "2001",
                                  "username": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.header.code").value("AIP-IAM-LOGINRATELIMITED"))
                .andExpect(jsonPath("$.header.message").value("登录尝试过于频繁，请稍后重试"));
    }
}
