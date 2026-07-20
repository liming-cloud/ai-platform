package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.UserInvitationApplicationService;
import com.aiplatform.identity.application.command.InviteTenantUserCommand;
import com.aiplatform.identity.presentation.dto.UserInvitationCreateRequest;
import com.aiplatform.identity.presentation.dto.UserInvitationResponse;
import com.aiplatform.identity.presentation.dto.UserInvitationOverviewResponse;
import com.aiplatform.identity.presentation.dto.UserInvitationRevokeRequest;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam/user-invitations")
public class UserInvitationController {
    private final UserInvitationApplicationService service;

    public UserInvitationController(UserInvitationApplicationService service) {
        this.service = Objects.requireNonNull(service, "用户邀请应用服务不能为空");
    }

    @PostMapping
    public ApiResponse<UserInvitationResponse> create(@Valid @RequestBody UserInvitationCreateRequest request) {
        return ApiResponse.success(UserInvitationResponse.from(service.invite(new InviteTenantUserCommand(
                TenantContext.current(), PrincipalContext.currentActorId(), TraceContext.currentTraceId(),
                request.username(), request.displayName(), request.email(), request.roleId()
        ))));
    }

    @GetMapping
    public ApiResponse<List<UserInvitationOverviewResponse>> list() {
        return ApiResponse.success(service.list(TenantContext.current()).stream().map(UserInvitationOverviewResponse::from).toList());
    }

    @PostMapping("/{invitationId}/resend")
    public ApiResponse<UserInvitationResponse> resend(@PathVariable String invitationId) {
        return ApiResponse.success(UserInvitationResponse.from(service.reissue(
                TenantContext.current(), invitationId, PrincipalContext.currentActorId())));
    }

    @PostMapping("/{invitationId}/revoke")
    public ApiResponse<Void> revoke(
            @PathVariable String invitationId,
            @Valid @RequestBody UserInvitationRevokeRequest request
    ) {
        service.revoke(
                TenantContext.current(),
                invitationId,
                PrincipalContext.currentActorId(),
                TraceContext.currentTraceId(),
                request.reason()
        );
        return ApiResponse.success(null);
    }
}
