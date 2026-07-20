package com.aiplatform.shared.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.AuthorizationRequestTarget;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.RequestableAuthorizationException;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.web.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void mapsInvalidSessionApplicationExceptionToUnauthorizedResponse() {
        TraceContext.ensureTraceId("TRACE-APP-ERROR-001");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApplicationException exception = new ApplicationException(
                ErrorCode.of("AIP-IAM-SESSIONINVALID", "登录态无效或已过期"),
                "登录态无效"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleApplication(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().header().code()).isEqualTo("AIP-IAM-SESSIONINVALID");
        assertThat(response.getBody().header().message()).isEqualTo("登录态无效或已过期");
        assertThat(response.getBody().header().traceId()).isEqualTo("TRACE-APP-ERROR-001");
    }

    @Test
    void mapsResourceNotVisibleContractToNotFoundResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApplicationException exception = new ApplicationException(
                ErrorCode.of("AIP-IAM-RESOURCE-5004", "资源不可见"),
                "目标资源不存在或无权查看"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleApplication(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().header().code()).isEqualTo("AIP-IAM-RESOURCE-5004");
    }

    @Test
    void exposesOnlyTheStructuredRequestableAuthorizationTargetOnForbiddenResponses() {
        TraceContext.ensureTraceId("TRACE-REQUESTABLE-AUTHZ-001");
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AuthorizationRequestTarget target = new AuthorizationRequestTarget(
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use"
        );
        RequestableAuthorizationException exception = new RequestableAuthorizationException(
                ErrorCode.of("AIP-IAM-AUTHZ-5003-REQUESTABLE", "当前操作可申请资源授权"),
                "resource_acl_denied",
                target
        );

        ResponseEntity<ApiResponse<AuthorizationRequestTarget>> response =
                handler.handleRequestableAuthorization(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().header().code()).isEqualTo("AIP-IAM-AUTHZ-5003-REQUESTABLE");
        assertThat(response.getBody().header().message()).isEqualTo("当前操作可申请资源授权");
        assertThat(response.getBody().header().traceId()).isEqualTo("TRACE-REQUESTABLE-AUTHZ-001");
        assertThat(response.getBody().body()).isEqualTo(target);
    }

    @Test
    void mapsLoginRateLimitToStableTooManyRequestsContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApplicationException exception = new ApplicationException(
                ErrorCode.of("AIP-IAM-LOGINRATELIMITED", "登录尝试过于频繁，请稍后重试"),
                "登录失败已达限制"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleApplication(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().header().code()).isEqualTo("AIP-IAM-LOGINRATELIMITED");
        assertThat(response.getBody().header().message()).isEqualTo("登录尝试过于频繁，请稍后重试");
    }

    @Test
    void mapsUnavailableAuthenticationProtectionToServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApplicationException exception = new ApplicationException(
                ErrorCode.of("AIP-IAM-AUTHPROTECTIONUNAVAILABLE", "登录服务暂时不可用，请稍后重试"),
                "登录防护存储不可用"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleApplication(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().header().code()).isEqualTo("AIP-IAM-AUTHPROTECTIONUNAVAILABLE");
    }

    @Test
    void mapsRecoverableApplicationConflictToConflictResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ApplicationException exception = new ApplicationException(
                ErrorCode.of("AIP-APP-CHAT-SESSION-CONFLICT", "应用已发布新版本，请新建会话后继续"),
                "session release changed"
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleApplication(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().header().code()).isEqualTo("AIP-APP-CHAT-SESSION-CONFLICT");
        assertThat(response.getBody().header().message()).isEqualTo("应用已发布新版本，请新建会话后继续");
        assertThat(response.getBody().body()).isNull();
    }

    @Test
    void mapsIllegalArgumentToFrozenValidationContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ApiResponse<Void> response = handler.handleIllegalArgument(new IllegalArgumentException("非法动作"));

        assertThat(response.header().code()).isEqualTo("AIP-COMMON-VALIDATION-2001");
        assertThat(response.header().message()).isEqualTo("参数校验失败");
    }
}
