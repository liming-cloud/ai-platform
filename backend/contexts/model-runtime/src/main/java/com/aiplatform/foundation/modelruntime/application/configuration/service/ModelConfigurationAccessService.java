package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.util.Objects;

/**
 * 管理模型配置使用权；撤销后活动授权查询立即失效。
 */
public final class ModelConfigurationAccessService {

    private static final PermissionCode PLATFORM_WRITE = new PermissionCode("model-platform:write");
    private static final PermissionCode TENANT_WRITE = new PermissionCode("model-tenant:write");

    private final ModelConfigurationRepository configurations;
    private final ModelConfigurationAccessRepository grants;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public ModelConfigurationAccessService(
            ModelConfigurationRepository configurations,
            ModelConfigurationAccessRepository grants,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置仓储不能为空");
        this.grants = Objects.requireNonNull(grants, "模型配置授权仓储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public ModelConfigurationAccessGrant grant(
            TrustedRequestContext context,
            ModelConfigurationId configurationId,
            TenantId granteeTenantId,
            ModelConfigurationOwnerId granteeMembershipId) {
        var configuration = loadManagedConfiguration(context, configurationId, granteeTenantId);
        if (configuration.scope() == ModelConfigurationScope.PERSONAL) {
            throw new ModelConfigurationNotFoundException();
        }
        var grant = ModelConfigurationAccessGrant.grant(
                new ModelConfigurationAccessGrantId(identifiers.nextId()),
                configuration.id(),
                Objects.requireNonNull(granteeTenantId, "受授权租户不能为空"),
                granteeMembershipId,
                clock.instant());
        grants.save(grant);
        return grant;
    }

    public ModelConfigurationAccessGrant revoke(
            TrustedRequestContext context, ModelConfigurationAccessGrantId grantId) {
        var existing = grants.findById(Objects.requireNonNull(grantId, "模型配置授权标识不能为空"))
                .orElseThrow(ModelConfigurationNotFoundException::new);
        var configuration = configurations.findConfiguration(existing.modelConfigurationId())
                .orElseThrow(ModelConfigurationNotFoundException::new);
        ensureCanManage(context, configuration, existing.granteeTenantId());
        var revoked = existing.revoke(clock.instant());
        if (revoked != existing) {
            grants.update(revoked);
        }
        return revoked;
    }

    public boolean mayUse(TrustedRequestContext context, ModelConfiguration configuration) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        return mayUse(
                context.tenantId(),
                new ModelConfigurationOwnerId(context.membershipId().value()),
                configuration);
    }

    /**
     * 异步任务使用持久化的创建成员重新检查模型配置授权，不接受调用方提交的身份字段。
     */
    public boolean mayUse(
            TenantId tenantId,
            ModelConfigurationOwnerId membershipId,
            ModelConfiguration configuration) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(membershipId, "成员标识不能为空");
        Objects.requireNonNull(configuration, "模型配置不能为空");
        if (configuration.status() != ModelConfigurationStatus.ACTIVE) {
            return false;
        }
        return switch (configuration.scope()) {
            case PERSONAL -> configuration.tenantId().equals(tenantId)
                    && configuration.ownerMembershipId().equals(membershipId);
            case TENANT -> configuration.tenantId().equals(tenantId);
            case PLATFORM -> grants.hasActiveGrant(
                    configuration.id(), tenantId, membershipId);
        };
    }

    private ModelConfiguration loadManagedConfiguration(
            TrustedRequestContext context, ModelConfigurationId configurationId, TenantId granteeTenantId) {
        var configuration = configurations.findConfiguration(
                        Objects.requireNonNull(configurationId, "模型配置标识不能为空"))
                .orElseThrow(ModelConfigurationNotFoundException::new);
        ensureCanManage(context, configuration, granteeTenantId);
        return configuration;
    }

    private static void ensureCanManage(
            TrustedRequestContext context, ModelConfiguration configuration, TenantId granteeTenantId) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (configuration.scope() == ModelConfigurationScope.PLATFORM
                && context.hasPermission(PLATFORM_WRITE)) {
            return;
        }
        if (configuration.scope() == ModelConfigurationScope.TENANT
                && context.hasPermission(TENANT_WRITE)
                && configuration.tenantId().equals(context.tenantId())
                && configuration.tenantId().equals(granteeTenantId)) {
            return;
        }
        throw new ModelConfigurationNotFoundException();
    }
}
