package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 由单一租户拥有的版本化自定义角色聚合根。
 *
 * <p>角色只保存已发布的租户权限集合；停用后不得用于新增绑定或再修改权限。成员权限与会话撤销不属于本根，
 * 由命名治理操作在外部协调。</p>
 */
public final class TenantCustomRole implements AggregateRoot<TenantCustomRoleId> {

    private final TenantCustomRoleId id;
    private final TenantId tenantId;
    private final String name;
    private final PermissionGrantSet grants;
    private final TenantCustomRoleStatus status;
    private final Instant createdAt;
    private final Instant disabledAt;

    private TenantCustomRole(
            TenantCustomRoleId id,
            TenantId tenantId,
            String name,
            PermissionGrantSet grants,
            TenantCustomRoleStatus status,
            Instant createdAt,
            Instant disabledAt) {
        this.id = Objects.requireNonNull(id, "租户自定义角色标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.name = requireName(name);
        this.grants = requirePublishedGrants(grants);
        this.status = Objects.requireNonNull(status, "租户自定义角色状态不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        if (status == TenantCustomRoleStatus.DISABLED && disabledAt == null) {
            throw new IllegalArgumentException("停用角色必须记录停用时间");
        }
        if (status == TenantCustomRoleStatus.ACTIVE && disabledAt != null) {
            throw new IllegalArgumentException("活动角色不能记录停用时间");
        }
        this.disabledAt = disabledAt;
    }

    /**
     * 创建可用于新成员绑定的租户自定义角色。
     *
     * @throws IllegalArgumentException 当名称或权限集合不符合租户角色目录约束时拒绝。
     */
    public static TenantCustomRole create(
            TenantCustomRoleId id,
            TenantId tenantId,
            String name,
            Set<PermissionCode> permissions,
            Instant createdAt) {
        return new TenantCustomRole(
                id,
                tenantId,
                name,
                new PermissionGrantSet(0L, permissions),
                TenantCustomRoleStatus.ACTIVE,
                createdAt,
                null);
    }

    /**
     * 恢复已持久化的租户自定义角色事实。
     *
     * @throws IllegalArgumentException 当持久化事实不满足状态或权限目录约束时拒绝。
     */
    public static TenantCustomRole reconstitute(
            TenantCustomRoleId id,
            TenantId tenantId,
            String name,
            PermissionGrantSet grants,
            TenantCustomRoleStatus status,
            Instant createdAt,
            Instant disabledAt) {
        return new TenantCustomRole(id, tenantId, name, grants, status, createdAt, disabledAt);
    }

    /**
     * 修改活动角色的权限集合并生成下一修订。
     *
     * @throws IllegalStateException 当角色已停用时拒绝修改。
     * @throws IllegalArgumentException 当权限集合为空或包含未发布权限时拒绝。
     */
    public TenantCustomRole changePermissions(Set<PermissionCode> permissions, Instant occurredAt) {
        if (!isActive()) {
            throw new IllegalStateException("停用角色不能修改权限");
        }
        Objects.requireNonNull(occurredAt, "操作时间不能为空");
        return new TenantCustomRole(
                id,
                tenantId,
                name,
                new PermissionGrantSet(grants.version() + 1L, permissions),
                status,
                createdAt,
                null);
    }

    /** 停用角色，使其不能再参与新的角色绑定。 */
    public TenantCustomRole disable(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "停用时间不能为空");
        if (!isActive()) {
            return this;
        }
        return new TenantCustomRole(
                id,
                tenantId,
                name,
                new PermissionGrantSet(grants.version() + 1L, grants.permissions()),
                TenantCustomRoleStatus.DISABLED,
                createdAt,
                occurredAt);
    }

    @Override
    public TenantCustomRoleId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public Set<PermissionCode> permissions() {
        return grants.permissions();
    }

    public long version() {
        return grants.version();
    }

    public TenantCustomRoleStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == TenantCustomRoleStatus.ACTIVE;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant disabledAt() {
        return disabledAt;
    }

    private static PermissionGrantSet requirePublishedGrants(PermissionGrantSet grants) {
        grants = Objects.requireNonNull(grants, "角色权限集合不能为空");
        if (grants.permissions().isEmpty()) {
            throw new IllegalArgumentException("租户自定义角色必须至少拥有一项权限");
        }
        if (!TenantPublishedPermission.containsAll(grants.permissions())) {
            throw new IllegalArgumentException("租户自定义角色包含未发布或非租户权限");
        }
        return grants;
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "角色名称不能为空");
        var normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 64) {
            throw new IllegalArgumentException("角色名称长度必须在 2 到 64 个字符之间");
        }
        return normalized;
    }
}
