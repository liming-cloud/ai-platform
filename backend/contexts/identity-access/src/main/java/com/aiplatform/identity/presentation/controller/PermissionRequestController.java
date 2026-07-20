package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.PermissionRequestApplicationService;
import com.aiplatform.identity.presentation.dto.PermissionRequestCreateRequest;
import com.aiplatform.identity.presentation.dto.PermissionRequestDecisionRequest;
import com.aiplatform.identity.presentation.dto.PermissionRequestResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permission-requests")
public class PermissionRequestController {
    private final PermissionRequestApplicationService service;

    public PermissionRequestController(PermissionRequestApplicationService service) {
        this.service = Objects.requireNonNull(service, "权限申请应用服务不能为空");
    }

    @PostMapping
    public ApiResponse<PermissionRequestResponse> create(@Valid @RequestBody PermissionRequestCreateRequest request) {
        return ApiResponse.success(PermissionRequestResponse.from(service.create(
                TenantContext.current(), PrincipalContext.currentActorId(), request.resourceType(), request.resourceId(),
                request.permissionCode(), request.reason()
        )));
    }

    @GetMapping
    public ApiResponse<PageResponse<PermissionRequestResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var result = service.list(TenantContext.current(), status, page, pageSize);
        return ApiResponse.success(new PageResponse<>(
                result.items().stream().map(PermissionRequestResponse::from).toList(),
                result.page(),
                result.pageSize(),
                result.total(),
                result.totalPages()
        ));
    }

    @PostMapping("/{requestId}/actions")
    public ApiResponse<PermissionRequestResponse> decide(
            @PathVariable String requestId,
            @Valid @RequestBody PermissionRequestDecisionRequest request
    ) {
        return ApiResponse.success(PermissionRequestResponse.from(service.decide(
                TenantContext.current(), PrincipalContext.currentActorId(), requestId, request.action(), request.comment(),
                TraceContext.currentTraceId()
        )));
    }
}
