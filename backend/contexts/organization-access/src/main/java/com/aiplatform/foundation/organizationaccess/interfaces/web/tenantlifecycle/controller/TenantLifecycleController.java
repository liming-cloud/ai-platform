package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleTransitionService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantProvisioningRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response.TenantLifecycleResponse;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受限的平台租户建立与生命周期状态迁移入口。
 *
 * <p>每个操作均先由参数解析器从 Bearer 令牌恢复 `PlatformRequestContext`；路径租户标识和请求中的
 * 初始管理员标识只是受管业务资源目标。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
public final class TenantLifecycleController {

    private final TenantProvisioningService provisioning;
    private final TenantLifecycleTransitionService transitions;

    public TenantLifecycleController(
            TenantProvisioningService provisioning,
            TenantLifecycleTransitionService transitions) {
        this.provisioning = Objects.requireNonNull(provisioning, "租户建立服务不能为空");
        this.transitions = Objects.requireNonNull(transitions, "生命周期迁移服务不能为空");
    }

    /**
     * 建立处于准备中状态的新租户。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantLifecycleResponse provision(
            PlatformRequestContext platform,
            @RequestBody Map<String, Object> payload) {
        var request = TenantProvisioningRequest.from(payload);
        return TenantLifecycleResponse.from(provisioning.provision(
                platform,
                request.tenantCode(),
                request.displayName(),
                request.initialAdministratorSubjectIdValue()));
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
