package com.aiplatform.audit.presentation.controller;

import com.aiplatform.audit.application.AuditSecurityLogQueryService;
import com.aiplatform.audit.application.result.AuditSecurityLogPageResult;
import com.aiplatform.audit.presentation.response.AuditSecurityLogResponse;
import com.aiplatform.audit.presentation.response.AuditDetailDisclosurePolicy;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.web.response.ApiResponse;
import com.aiplatform.shared.web.response.PageResponse;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安全审计日志查询入口，租户范围来自服务端上下文，禁止由请求参数覆盖。
 */
@RestController
@RequestMapping("/api/v1/audit/security-logs")
public class AuditSecurityLogController {
    private final AuditSecurityLogQueryService service;
    private final AuditDetailDisclosurePolicy detailDisclosurePolicy;

    public AuditSecurityLogController(
            AuditSecurityLogQueryService service,
            AuditDetailDisclosurePolicy detailDisclosurePolicy
    ) {
        this.service = Objects.requireNonNull(service, "安全审计日志查询服务不能为空");
        this.detailDisclosurePolicy = Objects.requireNonNull(detailDisclosurePolicy, "安全审计详情披露策略不能为空");
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditSecurityLogResponse>> listLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        AuditSecurityLogPageResult result = service.findLogs(
                TenantContext.current(),
                actorId,
                eventType,
                subjectType,
                subjectId,
                traceId,
                riskLevel,
                resultStatus,
                createdFrom,
                createdTo,
                page,
                pageSize
        );
        return ApiResponse.success(new PageResponse<>(
                result.items()
                .stream()
                .map(item -> AuditSecurityLogResponse.from(
                        item,
                        detailDisclosurePolicy.disclose(item.detailJson())
                ))
                .toList(),
                result.page(),
                result.pageSize(),
                result.total(),
                result.totalPages()
        ));
    }
}
