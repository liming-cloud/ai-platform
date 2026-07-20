package com.aiplatform.approval.presentation.controller;

import com.aiplatform.approval.application.ApprovalTaskApplicationService;
import com.aiplatform.approval.application.command.DecideApprovalTaskCommand;
import com.aiplatform.approval.application.command.ListApprovalTasksCommand;
import com.aiplatform.approval.presentation.converter.ApprovalPresentationConverter;
import com.aiplatform.approval.presentation.dto.ApprovalDecisionResponse;
import com.aiplatform.approval.presentation.dto.ApprovalTaskActionRequest;
import com.aiplatform.approval.presentation.dto.ApprovalTaskResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.shared.web.response.PageResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批任务 HTTP 适配器，负责从服务端上下文读取租户和审批人。
 */
@RestController
@RequestMapping("/api/v1/approvals/tasks")
public class ApprovalTaskController {
    private final ApprovalTaskApplicationService service;
    private final ApprovalPresentationConverter converter;

    public ApprovalTaskController(
            ApprovalTaskApplicationService service,
            ApprovalPresentationConverter converter
    ) {
        this.service = Objects.requireNonNull(service, "审批任务应用服务不能为空");
        this.converter = Objects.requireNonNull(converter, "审批展示转换器不能为空");
    }

    @GetMapping
    public ApiResponse<PageResponse<ApprovalTaskResponse>> listTasks(
            @RequestParam Optional<String> status,
            @RequestParam Optional<String> riskLevel,
            @RequestParam Optional<String> sourceContext,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var result = service.listTasks(new ListApprovalTasksCommand(
                TenantContext.current(),
                status,
                riskLevel,
                sourceContext,
                keyword,
                page,
                pageSize
        ));
        var rows = result.items()
                .stream()
                .map(converter::toResponse)
                .toList();
        return ApiResponse.success(new PageResponse<>(
                rows,
                result.page(),
                result.pageSize(),
                result.total(),
                result.totalPages()
        ));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ApprovalTaskResponse> getTask(@PathVariable String taskId) {
        return ApiResponse.success(converter.toResponse(service.getTask(TenantContext.current(), taskId)));
    }

    @PostMapping("/{taskId}/actions")
    public ApiResponse<ApprovalDecisionResponse> decide(
            @PathVariable String taskId,
            @Valid @RequestBody ApprovalTaskActionRequest request
    ) {
        return ApiResponse.success(converter.toResponse(service.decide(new DecideApprovalTaskCommand(
                TenantContext.current(),
                PrincipalContext.currentActorId(),
                taskId,
                request.action(),
                request.comment(),
                request.expectedEvidenceDigest()
        ))));
    }
}
