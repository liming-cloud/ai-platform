package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台治理的租户生命周期聚合根。
 *
 * <p>本根只保存状态、初始管理员主体引用和可复核版本；知识、助手、成员对象图和正文不属于该聚合。</p>
 */
public final class TenantLifecycle implements AggregateRoot<TenantId> {

    private static final Pattern TENANT_CODE = Pattern.compile("[a-z][a-z0-9-]{2,62}");
    private static final int MAX_DISPLAY_NAME_CODE_POINTS = 128;

    private final TenantId id;
    private final String code;
    private final String displayName;
    private final SubjectId initialAdministratorId;
    private final TenantLifecycleStatus status;
    private final long version;
    private final Instant createdAt;

    private TenantLifecycle(
            TenantId id,
            String code,
            String displayName,
            SubjectId initialAdministratorId,
            TenantLifecycleStatus status,
            long version,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "租户标识不能为空");
        this.code = requireCode(code);
        this.displayName = requireDisplayName(displayName);
        this.initialAdministratorId = Objects.requireNonNull(initialAdministratorId, "初始管理员主体不能为空");
        this.status = Objects.requireNonNull(status, "租户生命周期状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("租户生命周期修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "租户创建时间不能为空");
    }

    /**
     * 建立尚未承接租户业务的租户生命周期。
     *
     * @throws IllegalArgumentException 当租户编码、显示名称或初始管理员不合法时拒绝。
     */
    public static TenantLifecycle provision(
            TenantId id,
            String code,
            String displayName,
            SubjectId initialAdministratorId,
            Instant createdAt) {
        return new TenantLifecycle(
                id,
                code,
                displayName,
                initialAdministratorId,
                TenantLifecycleStatus.PROVISIONING,
                0L,
                createdAt);
    }

    /**
     * 从持久化的生命周期事实恢复聚合根。
     *
     * @throws IllegalArgumentException 当持久化事实违反生命周期不变量时拒绝。
     */
    public static TenantLifecycle reconstitute(
            TenantId id,
            String code,
            String displayName,
            SubjectId initialAdministratorId,
            TenantLifecycleStatus status,
            long version,
            Instant createdAt) {
        return new TenantLifecycle(
                id, code, displayName, initialAdministratorId, status, version, createdAt);
    }

    /**
     * 将已完成初始管理员准备的租户激活。
     *
     * @throws IllegalStateException 当租户不是准备中状态时拒绝。
     */
    public TenantLifecycle activate() {
        return transition(TenantLifecycleStatus.PROVISIONING, TenantLifecycleStatus.ACTIVE, "只有准备中的租户可以激活");
    }

    /**
     * 挂起活跃租户；该状态不删除既有租户数据。
     *
     * @throws IllegalStateException 当租户不是活跃状态时拒绝。
     */
    public TenantLifecycle suspend() {
        return transition(TenantLifecycleStatus.ACTIVE, TenantLifecycleStatus.SUSPENDED, "只有活跃租户可以挂起");
    }

    /**
     * 恢复已挂起租户。
     *
     * @throws IllegalStateException 当租户不是挂起状态时拒绝。
     */
    public TenantLifecycle restore() {
        return transition(TenantLifecycleStatus.SUSPENDED, TenantLifecycleStatus.ACTIVE, "只有挂起租户可以恢复");
    }

    @Override
    public TenantId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public SubjectId initialAdministratorId() {
        return initialAdministratorId;
    }

    public TenantLifecycleStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private TenantLifecycle transition(
            TenantLifecycleStatus expected,
            TenantLifecycleStatus target,
            String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
        return new TenantLifecycle(
                id, code, displayName, initialAdministratorId, target, version + 1L, createdAt);
    }

    private static String requireCode(String value) {
        Objects.requireNonNull(value, "租户编码不能为空");
        if (!TENANT_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("租户编码格式不正确");
        }
        return value;
    }

    private static String requireDisplayName(String value) {
        Objects.requireNonNull(value, "租户显示名称不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_DISPLAY_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("租户显示名称格式不正确");
        }
        return value;
    }
}
