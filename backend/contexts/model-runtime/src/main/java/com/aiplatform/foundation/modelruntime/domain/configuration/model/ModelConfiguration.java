package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 绑定受控目录条目与明确所有权范围的模型配置聚合根。
 */
public final class ModelConfiguration implements AggregateRoot<ModelConfigurationId> {

    private static final int MAX_NAME_CODE_POINTS = 160;

    private final ModelConfigurationId id;
    private final ModelConfigurationScope scope;
    private final TenantId tenantId;
    private final ModelConfigurationOwnerId ownerMembershipId;
    private final String displayName;
    private final ControlledModelCatalogEntryId catalogEntryId;
    private final ModelConfigurationStatus status;
    private final long version;
    private final Instant createdAt;

    private ModelConfiguration(
            ModelConfigurationId id,
            ModelConfigurationScope scope,
            TenantId tenantId,
            ModelConfigurationOwnerId ownerMembershipId,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId,
            ModelConfigurationStatus status,
            long version,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "模型配置标识不能为空");
        this.scope = Objects.requireNonNull(scope, "模型配置范围不能为空");
        validateOwnership(scope, tenantId, ownerMembershipId);
        this.tenantId = tenantId;
        this.ownerMembershipId = ownerMembershipId;
        this.displayName = requireDisplayName(displayName);
        this.catalogEntryId = Objects.requireNonNull(catalogEntryId, "受控模型目录条目标识不能为空");
        this.status = Objects.requireNonNull(status, "模型配置状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("模型配置版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static ModelConfiguration create(
            ModelConfigurationId id,
            ModelConfigurationScope scope,
            TenantId tenantId,
            ModelConfigurationOwnerId ownerMembershipId,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId,
            Instant createdAt) {
        return new ModelConfiguration(
                id,
                scope,
                tenantId,
                ownerMembershipId,
                displayName,
                catalogEntryId,
                ModelConfigurationStatus.DRAFT,
                0,
                createdAt);
    }

    @Override
    public ModelConfigurationId id() {
        return id;
    }

    public ModelConfigurationScope scope() {
        return scope;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ModelConfigurationOwnerId ownerMembershipId() {
        return ownerMembershipId;
    }

    public String displayName() {
        return displayName;
    }

    public ControlledModelCatalogEntryId catalogEntryId() {
        return catalogEntryId;
    }

    public ModelConfigurationStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static void validateOwnership(
            ModelConfigurationScope scope,
            TenantId tenantId,
            ModelConfigurationOwnerId ownerMembershipId) {
        if (scope == ModelConfigurationScope.PLATFORM
                && (tenantId != null || ownerMembershipId != null)) {
            throw new IllegalArgumentException("平台模型配置不能绑定租户或成员");
        }
        if (scope == ModelConfigurationScope.TENANT
                && (tenantId == null || ownerMembershipId != null)) {
            throw new IllegalArgumentException("租户模型配置必须且只能绑定租户");
        }
        if (scope == ModelConfigurationScope.PERSONAL
                && (tenantId == null || ownerMembershipId == null)) {
            throw new IllegalArgumentException("个人模型配置必须绑定租户和成员");
        }
    }

    private static String requireDisplayName(String value) {
        Objects.requireNonNull(value, "模型配置名称不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("模型配置名称格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029') {
                throw new IllegalArgumentException("模型配置名称不能包含控制或换行字符");
            }
        }
        return value;
    }
}
