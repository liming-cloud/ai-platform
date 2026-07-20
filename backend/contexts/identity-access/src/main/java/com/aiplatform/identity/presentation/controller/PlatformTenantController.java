package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.IamConfigurationCommandApplicationService;
import com.aiplatform.identity.application.IamConfigurationQueryApplicationService;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.application.command.CreateIamTenantCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantCommand;
import com.aiplatform.identity.application.command.ChangeIamTenantStatusCommand;
import com.aiplatform.identity.presentation.dto.IamConfigurationMutationResponse;
import com.aiplatform.identity.presentation.dto.IamTenantFeatureResponse;
import com.aiplatform.identity.presentation.dto.IamTenantFeatureUpdateRequest;
import com.aiplatform.identity.presentation.dto.IamTenantResponse;
import com.aiplatform.identity.presentation.dto.IamTenantCreateRequest;
import com.aiplatform.identity.presentation.dto.IamTenantUpdateRequest;
import com.aiplatform.identity.presentation.dto.IamTenantStatusRequest;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam/tenants")
public class PlatformTenantController {
    private final IamConfigurationQueryApplicationService queryService;
    private final IamConfigurationCommandApplicationService commandService;

    public PlatformTenantController(IamConfigurationQueryApplicationService queryService, IamConfigurationCommandApplicationService commandService) {
        this.queryService = Objects.requireNonNull(queryService, "IAM 配置查询服务不能为空");
        this.commandService = Objects.requireNonNull(commandService, "IAM 配置命令服务不能为空");
    }

    @GetMapping
    public ApiResponse<PageResponse<IamTenantResponse>> listTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status
    ) {
        String normalizedKeyword = keyword.trim().toLowerCase();
        String normalizedStatus = status.trim().toUpperCase();
        List<IamTenantResponse> rows = queryService.listTenants().stream().map(IamTenantResponse::from)
                .filter(row -> normalizedKeyword.isEmpty() || row.name().toLowerCase().contains(normalizedKeyword) || row.code().toLowerCase().contains(normalizedKeyword))
                .filter(row -> normalizedStatus.isEmpty() || row.status().equalsIgnoreCase(normalizedStatus))
                .toList();
        return ApiResponse.success(PageResponse.from(rows, page, pageSize));
    }

    @PostMapping
    public ApiResponse<IamTenantResponse> createTenant(@Valid @RequestBody IamTenantCreateRequest request) {
        String tenantId = commandService.createTenant(new CreateIamTenantCommand(
                PrincipalContext.currentActorId(), TraceContext.currentTraceId(), request.code(), request.name(), request.planCode()));
        return ApiResponse.success(queryService.listTenants().stream().filter(row -> row.tenantId().equals(tenantId)).map(IamTenantResponse::from).findFirst().orElseThrow());
    }

    @PutMapping("/{tenantId}")
    public ApiResponse<IamConfigurationMutationResponse> updateTenant(@PathVariable String tenantId, @Valid @RequestBody IamTenantUpdateRequest request) {
        commandService.updateTenant(new UpdateIamTenantCommand(TenantId.of(tenantId), PrincipalContext.currentActorId(), TraceContext.currentTraceId(), request.name(), request.planCode()));
        return ApiResponse.success(new IamConfigurationMutationResponse(tenantId, "SAVED"));
    }

    @PostMapping("/{tenantId}/status")
    public ApiResponse<IamConfigurationMutationResponse> changeTenantStatus(@PathVariable String tenantId, @Valid @RequestBody IamTenantStatusRequest request) {
        commandService.changeTenantStatus(new ChangeIamTenantStatusCommand(TenantId.of(tenantId), PrincipalContext.currentActorId(), TraceContext.currentTraceId(), request.status(), request.reason()));
        return ApiResponse.success(new IamConfigurationMutationResponse(tenantId, request.status()));
    }

    @GetMapping("/{tenantId}/features")
    public ApiResponse<List<IamTenantFeatureResponse>> listFeatures(@PathVariable String tenantId) {
        return ApiResponse.success(queryService.listTenantFeatures(TenantId.of(tenantId)).stream().map(IamTenantFeatureResponse::from).toList());
    }

    @PutMapping("/{tenantId}/features/{featureCode}")
    public ApiResponse<IamConfigurationMutationResponse> updateFeature(
            @PathVariable String tenantId,
            @PathVariable String featureCode,
            @Valid @RequestBody IamTenantFeatureUpdateRequest request
    ) {
        commandService.updateTenantFeature(new UpdateIamTenantFeatureCommand(
                TenantId.of(tenantId), PrincipalContext.currentActorId(), TraceContext.currentTraceId(), featureCode, request.enabled(), request.reason()
        ));
        return ApiResponse.success(new IamConfigurationMutationResponse(featureCode, "SAVED"));
    }
}
