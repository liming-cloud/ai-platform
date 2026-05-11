package org.fujay.ai.platform.model.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.fujay.ai.platform.model.application.service.ModelProviderApplicationService;
import org.fujay.ai.platform.model.interfaces.assembler.ModelProviderAssembler;
import org.fujay.ai.platform.model.interfaces.request.SavePlatformModelOptionRequest;
import org.fujay.ai.platform.model.interfaces.request.SavePlatformProviderTypeRequest;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse.ModelOptionResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse.ProviderTypeOptionResponse;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-catalog")
public class PlatformModelCatalogController {

    private final ModelProviderApplicationService applicationService;
    private final ModelProviderAssembler assembler;

    public PlatformModelCatalogController(
            ModelProviderApplicationService applicationService, ModelProviderAssembler assembler) {
        this.applicationService = applicationService;
        this.assembler = assembler;
    }

    @GetMapping
    @SaCheckPermission("model:platform-catalog:read")
    public ApiResponse<ModelProviderOptionsResponse> listPlatformCatalog() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.listPlatformCatalog()));
    }

    @PostMapping("/provider-types")
    @SaCheckPermission("model:platform-catalog:write")
    public ApiResponse<ProviderTypeOptionResponse> saveProviderType(
            @RequestBody SavePlatformProviderTypeRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.savePlatformProviderType(assembler.toCommand(request))));
    }

    @PostMapping("/model-options")
    @SaCheckPermission("model:platform-catalog:write")
    public ApiResponse<ModelOptionResponse> saveModelOption(@RequestBody SavePlatformModelOptionRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.savePlatformModelOption(assembler.toCommand(request))));
    }
}
