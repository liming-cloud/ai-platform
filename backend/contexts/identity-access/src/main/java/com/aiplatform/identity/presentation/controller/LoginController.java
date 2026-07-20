package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.IdentitySessionApplicationService;
import com.aiplatform.identity.application.LoginApplicationService;
import com.aiplatform.identity.application.command.CurrentUserCommand;
import com.aiplatform.identity.application.command.LoginCommand;
import com.aiplatform.identity.application.command.LogoutCommand;
import com.aiplatform.identity.presentation.dto.CurrentUserResponse;
import com.aiplatform.identity.presentation.dto.LoginRequest;
import com.aiplatform.identity.presentation.dto.LoginResponse;
import com.aiplatform.identity.presentation.dto.LogoutResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份登录 HTTP 适配器，只负责请求校验、命令转换和统一响应包装。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final LoginApplicationService loginService;
    private final IdentitySessionApplicationService sessionService;

    public LoginController(LoginApplicationService loginService, IdentitySessionApplicationService sessionService) {
        this.loginService = Objects.requireNonNull(loginService, "登录应用服务不能为空");
        this.sessionService = Objects.requireNonNull(sessionService, "身份会话应用服务不能为空");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginCommand command = new LoginCommand(
                TenantId.of(request.tenantCode()),
                request.username(),
                request.password(),
                TraceContext.currentTraceId()
        );
        LoginResponse response = LoginResponse.from(loginService.login(command));
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(
            @RequestHeader(AUTHORIZATION_HEADER) String authorization
    ) {
        CurrentUserCommand command = new CurrentUserCommand(
                TenantContext.current(),
                extractTokenValue(authorization),
                TraceContext.currentTraceId()
        );
        return ApiResponse.success(CurrentUserResponse.from(sessionService.currentUser(command)));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(
            @RequestHeader(AUTHORIZATION_HEADER) String authorization
    ) {
        LogoutCommand command = new LogoutCommand(
                TenantContext.current(),
                extractTokenValue(authorization),
                TraceContext.currentTraceId()
        );
        sessionService.logout(command);
        return ApiResponse.success(LogoutResponse.success());
    }

    private static String extractTokenValue(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("Authorization 请求头不能为空");
        }
        String tokenValue = authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : authorization.trim();
        if (tokenValue.isBlank()) {
            throw new IllegalArgumentException("Authorization 请求头缺少访问令牌");
        }
        return tokenValue;
    }
}
