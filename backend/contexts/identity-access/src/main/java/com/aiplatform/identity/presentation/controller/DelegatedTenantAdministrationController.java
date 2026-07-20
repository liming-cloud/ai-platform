package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.DelegatedTenantAdministrationApplicationService;
import com.aiplatform.identity.application.command.ExecuteDelegatedTenantActionCommand;
import com.aiplatform.identity.presentation.dto.DelegatedTenantActionRequest;
import com.aiplatform.identity.presentation.dto.DelegatedTenantActionResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台管理员代管租户 HTTP 适配器。路径租户只表达目标资源，操作者租户始终来自服务端上下文。
 */
@RestController
@RequestMapping("/api/v1/iam/tenants")
public class DelegatedTenantAdministrationController {
    private final DelegatedTenantAdministrationApplicationService service;

    public DelegatedTenantAdministrationController(DelegatedTenantAdministrationApplicationService service) {
        this.service = Objects.requireNonNull(service, "平台代管租户应用服务不能为空");
    }

    @PostMapping("/{tenantId}/delegated-admin-actions")
    public ApiResponse<DelegatedTenantActionResponse> execute(
            @PathVariable String tenantId,
            @Valid @RequestBody DelegatedTenantActionRequest request
    ) {
        ExecuteDelegatedTenantActionCommand command = new ExecuteDelegatedTenantActionCommand(
                TenantContext.current(),
                TenantId.of(tenantId),
                PrincipalContext.requireCurrent().actorId(),
                TraceContext.currentTraceId(),
                request.actionType(),
                request.targetRoleId(),
                request.targetUserId(),
                request.permissionCodes(),
                request.reason(),
                request.idempotencyKey()
        );
        return ApiResponse.success(DelegatedTenantActionResponse.from(service.execute(command)));
    }
}
