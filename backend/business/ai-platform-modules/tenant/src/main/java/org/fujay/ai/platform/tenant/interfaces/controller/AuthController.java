package org.fujay.ai.platform.tenant.interfaces.controller;

import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.fujay.ai.platform.tenant.application.command.ChangePasswordCommand;
import org.fujay.ai.platform.tenant.application.command.LoginCommand;
import org.fujay.ai.platform.tenant.application.service.TenantAuthApplicationService;
import org.fujay.ai.platform.tenant.interfaces.assembler.TenantAccessAssembler;
import org.fujay.ai.platform.tenant.interfaces.request.ChangePasswordRequest;
import org.fujay.ai.platform.tenant.interfaces.request.LoginRequest;
import org.fujay.ai.platform.tenant.interfaces.response.CurrentUserResponse;
import org.fujay.ai.platform.tenant.interfaces.response.LoginResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TenantAuthApplicationService authService;
    private final TenantAccessAssembler assembler;

    public AuthController(TenantAuthApplicationService authService, TenantAccessAssembler assembler) {
        this.authService = authService;
        this.assembler = assembler;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = assembler.toLoginResponse(authService.login(
                new LoginCommand(request.tenantCode(), request.username(), request.password())));
        return ApiResponse.success(TraceContext.currentTraceId(), response);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toCurrentUserResponse(authService.currentUser()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(TraceContext.currentTraceId(), null);
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(new ChangePasswordCommand(request.oldPassword(), request.newPassword()));
        return ApiResponse.success(TraceContext.currentTraceId(), null);
    }
}
