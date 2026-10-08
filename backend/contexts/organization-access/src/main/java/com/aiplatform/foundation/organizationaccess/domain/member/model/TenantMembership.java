package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一个全局主体在单一租户内的可定位成员资格聚合根。
 *
 * <p>成员资格只引用主体标识，绝不持有登录名或凭据摘要。初始管理员资格只能由
 * {@code TenantProvisioning} 建立，避免应用层分别拼接生命周期和成员事实。</p>
 */
public final class TenantMembership implements AggregateRoot<MemberId> {

    private final MemberId id;
    private final TenantId tenantId;
    private final SubjectId subjectId;
    private final TenantMembershipRole role;
    private final TenantMembershipStatus status;
    private final long permissionVersion;
    private final long version;
    private final Instant createdAt;

    private TenantMembership(
            MemberId id,
            TenantId tenantId,
            SubjectId subjectId,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            long permissionVersion,
            long version,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "成员标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        this.role = Objects.requireNonNull(role, "成员角色不能为空");
        this.status = Objects.requireNonNull(status, "成员状态不能为空");
        if (permissionVersion < 0L || version < 0L) {
            throw new IllegalArgumentException("成员资格修订不能为负数");
        }
        this.permissionVersion = permissionVersion;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "成员创建时间不能为空");
    }

    /**
     * 建立租户的初始管理员成员资格。
     */
    public static TenantMembership establishInitialAdministrator(
            MemberId id,
            TenantId tenantId,
            SubjectId subjectId,
            Instant createdAt) {
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                TenantMembershipRole.TENANT_ADMINISTRATOR,
                TenantMembershipStatus.ENABLED,
                0L,
                0L,
                createdAt);
    }

    /**
     * 从持久化成员资格事实恢复聚合根。
     *
     * @throws IllegalArgumentException 当持久化成员资格违反修订或必填约束时拒绝。
     */
    public static TenantMembership reconstitute(
            MemberId id,
            TenantId tenantId,
            SubjectId subjectId,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            long permissionVersion,
            long version,
            Instant createdAt) {
        return new TenantMembership(
                id, tenantId, subjectId, role, status, permissionVersion, version, createdAt);
    }

    /**
     * 将已启用的既有成员资格确认成租户初始管理员。
     *
     * <p>该操作不创建第二条成员资格，也不改变主体、租户或权限修订；历史回填只能在
     * `TenantLifecycleBackfill` 已验证主体与租户归属后调用。</p>
     *
     * @throws IllegalStateException 当成员资格未启用时拒绝。
     */
    public TenantMembership confirmInitialAdministrator() {
        if (status != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("未启用成员资格不能确认为初始管理员");
        }
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                TenantMembershipRole.TENANT_ADMINISTRATOR,
                status,
                permissionVersion,
                version + 1L,
                createdAt);
    }

    /**
     * 在角色绑定或撤销成功后生成新的权限版本。
     *
     * @throws IllegalStateException 当成员资格未启用时拒绝修改权限事实。
     */
    public TenantMembership revisePermissions() {
        if (status != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("未启用成员不能修改权限");
        }
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                role,
                status,
                permissionVersion + 1L,
                version + 1L,
                createdAt);
    }

    /**
     * 普通成员保存对话配置时推进权限修订；停用状态保持不变，不能借编辑恢复资格。
     *
     * @throws IllegalStateException 当目标为管理员资格时拒绝。
     */
    public TenantMembership reviseConversationFeatures() {
        if (role != TenantMembershipRole.TENANT_USER) {
            throw new IllegalStateException("管理员不能保存普通成员对话功能");
        }
        return new TenantMembership(id, tenantId, subjectId, role, status,
                permissionVersion + 1L, version + 1L, createdAt);
    }

    /**
     * 对象授权变化仅推进本普通资格的版本，不改变启停状态或固定角色。
     * @throws IllegalStateException 管理员不属于普通成员授权目标。
     */
    public TenantMembership reviseAssistantAuthorization() {
        if (role != TenantMembershipRole.TENANT_USER) throw new IllegalStateException("管理员不能修改普通成员助手授权");
        return new TenantMembership(id, tenantId, subjectId, role, status,
                permissionVersion + 1L, version + 1L, createdAt);
    }

    /**
     * 停用当前租户成员资格，并推进会话必须校验的权限修订。
     *
     * <p>是否为最后一个访问管理员是跨成员不变量，由受锁成员目录上的领域服务在调用前保护。</p>
     */
    public TenantMembership disable() {
        if (status == TenantMembershipStatus.DISABLED) {
            return this;
        }
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                role,
                TenantMembershipStatus.DISABLED,
                permissionVersion + 1L,
                version + 1L,
                createdAt);
    }

    /**
     * 重新启用当前租户成员资格，并推进权限修订；不会恢复任何既已撤销的会话。
     */
    public TenantMembership enable() {
        if (status == TenantMembershipStatus.ENABLED) {
            return this;
        }
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                role,
                TenantMembershipStatus.ENABLED,
                permissionVersion + 1L,
                version + 1L,
                createdAt);
    }

    /**
     * 将当前租户成员资格转为启用的租户管理员，并只推进一次权限与聚合修订。
     *
     * <p>该转换只能由平台拥有的租户管理员治理领域服务调用；租户范围命令不得借此提升普通成员。</p>
     */
    public TenantMembership assignAdministrator() {
        if (role == TenantMembershipRole.TENANT_ADMINISTRATOR
                && status == TenantMembershipStatus.ENABLED) {
            return this;
        }
        return new TenantMembership(
                id,
                tenantId,
                subjectId,
                TenantMembershipRole.TENANT_ADMINISTRATOR,
                TenantMembershipStatus.ENABLED,
                permissionVersion + 1L,
                version + 1L,
                createdAt);
    }

    @Override
    public MemberId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public SubjectId subjectId() {
        return subjectId;
    }

    public TenantMembershipRole role() {
        return role;
    }

    public TenantMembershipStatus status() {
        return status;
    }

    public long permissionVersion() {
        return permissionVersion;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
