package org.fujay.ai.platform.model.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.fujay.ai.platform.model.application.service.ModelInvocationApplicationService;
import org.fujay.ai.platform.model.interfaces.assembler.ModelInvocationAssembler;
import org.fujay.ai.platform.model.interfaces.request.ChatInvocationRequest;
import org.fujay.ai.platform.model.interfaces.response.ChatInvocationResponse;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-invocations")
public class ModelInvocationController {

    private final ModelInvocationApplicationService applicationService;
    private final ModelInvocationAssembler assembler;

    public ModelInvocationController(
            ModelInvocationApplicationService applicationService,
            ModelInvocationAssembler assembler) {
        this.applicationService = applicationService;
        this.assembler = assembler;
    }

    @PostMapping("/chat")
    @SaCheckPermission("model:invocation:execute")
    public ApiResponse<ChatInvocationResponse> invokeChat(@RequestBody ChatInvocationRequest request) {
        return ApiResponse.success(
                TraceContext.currentTraceId(),
                assembler.toResponse(applicationService.invokeChat(assembler.toCommand(request))));
    }
}
