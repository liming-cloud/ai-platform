package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantQuotaView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleTransitionService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantProvisioningRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response.TenantLifecycleResponse;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response.TenantLifecycleSummaryResponse;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受限的平台租户建立与生命周期状态迁移入口。
 *
 * <p>操作者只由认证参数解析器恢复，开户不接受任何候选主体、角色或身份覆盖字段。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
public final class TenantLifecycleController {

    private final TenantProvisioningService provisioning;
    private final TenantLifecycleTransitionService transitions;
    private final TenantLifecycleQueryService lifecycleQueries;

    public TenantLifecycleController(
            TenantProvisioningService provisioning,
            TenantLifecycleTransitionService transitions,
            TenantLifecycleQueryService lifecycleQueries) {
        this.provisioning = Objects.requireNonNull(provisioning, "租户建立服务不能为空");
        this.transitions = Objects.requireNonNull(transitions, "生命周期迁移服务不能为空");
        this.lifecycleQueries = Objects.requireNonNull(lifecycleQueries, "生命周期读取服务不能为空");
    }

    /**
     * 分页读取已受权平台会话可见的最小生命周期投影。
     */
    @GetMapping
    public PageResult<TenantLifecycleSummaryResponse> list(
            PlatformRequestContext platform,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        var page = lifecycleQueries.list(platform, new PageQuery(pageNumber, pageSize));
        return new PageResult<>(
                page.items().stream().map(TenantLifecycleSummaryResponse::from).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.hasPrevious(),
                page.hasNext());
    }

    /**
     * 一次提交建立可用租户和初始管理员；不在 HTTP 层拆分事务步骤。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantLifecycleResponse provision(
            PlatformRequestContext platform,
            @RequestBody Map<String, Object> payload) {
        try (var request = TenantProvisioningRequest.from(payload)) {
            return TenantLifecycleResponse.from(provisioning.provision(
                    platform, request.command(), request.passwordCopy()));
        }
    }

    /** 读取租户概览使用的原生命周期投影。 */
    @GetMapping("/{tenantId}")
    public TenantLifecycleResponse find(PlatformRequestContext platform, @PathVariable long tenantId) {
        return TenantLifecycleResponse.from(lifecycleQueries.find(platform, tenantId));
    }

    /** 独立读取已保存配额，不返回租户正文或登录凭据。 */
    @GetMapping("/{tenantId}/quotas")
    public TenantQuotaView quotas(PlatformRequestContext platform, @PathVariable long tenantId) {
        return lifecycleQueries.quotas(platform, tenantId);
    }

    /**
     * 激活已完成初始化的新租户。
     */
    @PostMapping("/{tenantId}/lifecycle/activate")
    public TenantLifecycleResponse activate(
            PlatformRequestContext platform,
            @PathVariable String tenantId) {
        return transition(() -> transitions.activate(platform, tenantId));
    }

    /**
     * 挂起一个活跃租户，而不删除其既有数据。
     */
    @PostMapping("/{tenantId}/lifecycle/suspend")
    public TenantLifecycleResponse suspend(
            PlatformRequestContext platform,
            @PathVariable String tenantId) {
        return transition(() -> transitions.suspend(platform, tenantId));
    }

    /**
     * 恢复一个已挂起租户。
     */
    @PostMapping("/{tenantId}/lifecycle/restore")
    public TenantLifecycleResponse restore(
            PlatformRequestContext platform,
            @PathVariable String tenantId) {
        return transition(() -> transitions.restore(platform, tenantId));
    }

    private static TenantLifecycleResponse transition(Supplier<TenantLifecycleView> operation) {
        try {
            return TenantLifecycleResponse.from(operation.get());
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("租户生命周期不允许当前操作");
        }
    }
}
