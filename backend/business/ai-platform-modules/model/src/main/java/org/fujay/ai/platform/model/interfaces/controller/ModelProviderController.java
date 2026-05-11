package org.fujay.ai.platform.model.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.util.List;
import org.fujay.ai.platform.model.application.service.ModelProviderApplicationService;
import org.fujay.ai.platform.model.interfaces.assembler.ModelProviderAssembler;
import org.fujay.ai.platform.model.interfaces.request.SaveModelProviderRequest;
import org.fujay.ai.platform.model.interfaces.request.TestModelRequest;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelTestResponse;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-providers")
public class ModelProviderController {

    private final ModelProviderApplicationService applicationService;
    private final ModelProviderAssembler assembler;

    public ModelProviderController(ModelProviderApplicationService applicationService, ModelProviderAssembler assembler) {
        this.applicationService = applicationService;
        this.assembler = assembler;
    }

    @GetMapping
    @SaCheckPermission("model:tenant-config:read")
    public ApiResponse<List<ModelProviderResponse>> listProviders() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                applicationService.listProviders().stream().map(assembler::toResponse).toList());
    }

    @GetMapping("/options")
    @SaCheckPermission("model:tenant-config:read")
    public ApiResponse<ModelProviderOptionsResponse> listOptions() {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.listProviderOptions()));
    }

    @PostMapping
    @SaCheckPermission("model:tenant-config:write")
    public ApiResponse<ModelProviderResponse> saveProvider(@RequestBody SaveModelProviderRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.saveProvider(assembler.toCommand(request))));
    }

    @PostMapping("/test")
    @SaCheckPermission("model:tenant-config:write")
    public ApiResponse<ModelTestResponse> testModel(@RequestBody TestModelRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.testModel(assembler.toCommand(request))));
    }
}
