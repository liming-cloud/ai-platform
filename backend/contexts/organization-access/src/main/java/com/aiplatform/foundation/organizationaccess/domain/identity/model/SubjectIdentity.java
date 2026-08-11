package com.aiplatform.foundation.organizationaccess.domain.identity.model;

import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 跨租户可认证主体的唯一聚合根。
 *
 * <p>凭据只由本根保存一次；成员资格只能持有 {@link SubjectId}，从而禁止通过复制密码摘要建立新租户管理员。</p>
 */
public final class SubjectIdentity implements AggregateRoot<SubjectId> {

    private static final Pattern LOGIN_NAME = Pattern.compile("[a-z][a-z0-9._-]{2,63}");

    private final SubjectId id;
    private final String loginName;
    private final EncodedPassword encodedPassword;
    private final SubjectIdentityStatus status;
    private final long version;
    private final Instant createdAt;

    private SubjectIdentity(
            SubjectId id,
            String loginName,
            EncodedPassword encodedPassword,
            SubjectIdentityStatus status,
            long version,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "主体标识不能为空");
        this.loginName = requireLoginName(loginName);
        this.encodedPassword = Objects.requireNonNull(encodedPassword, "主体凭据摘要不能为空");
        this.status = Objects.requireNonNull(status, "主体状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("主体修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "主体创建时间不能为空");
    }

    /**
     * 注册可认证全局主体。
     *
     * @throws IllegalArgumentException 当登录名、凭据或标识不符合全局主体约束时拒绝。
     */
    public static SubjectIdentity register(
            SubjectId id, String loginName, EncodedPassword encodedPassword, Instant createdAt) {
        return new SubjectIdentity(
                id, loginName, encodedPassword, SubjectIdentityStatus.ACTIVE, 0L, createdAt);
    }

    /**
     * 从持久化的全局身份事实恢复聚合根。
     *
     * @throws IllegalArgumentException 当持久化事实违反主体不变量时拒绝。
     */
    public static SubjectIdentity reconstitute(
            SubjectId id,
            String loginName,
            EncodedPassword encodedPassword,
            SubjectIdentityStatus status,
            long version,
            Instant createdAt) {
        return new SubjectIdentity(id, loginName, encodedPassword, status, version, createdAt);
    }

    /**
     * 禁用主体后，所有依赖该凭据的新认证都必须失败关闭。
     */
    public SubjectIdentity disable() {
        if (status == SubjectIdentityStatus.DISABLED) {
            return this;
        }
        return new SubjectIdentity(
                id, loginName, encodedPassword, SubjectIdentityStatus.DISABLED, version + 1L, createdAt);
    }

    /**
     * 重新启用已禁用主体。
     *
     * @throws IllegalStateException 当主体并非禁用状态时拒绝重复启用。
     */
    public SubjectIdentity enable() {
        if (status == SubjectIdentityStatus.ACTIVE) {
            throw new IllegalStateException("活动账户不能重复启用");
        }
        return new SubjectIdentity(
                id, loginName, encodedPassword, SubjectIdentityStatus.ACTIVE, version + 1L, createdAt);
    }

    /**
     * 使用已编码的新凭据替换旧凭据。
     *
     * <p>调用方必须已完成受控密码重置的授权和原子会话撤销；本根只保证凭据事实与修订一致。</p>
     *
     * @throws IllegalArgumentException 当新凭据为空或不合法时拒绝。
     */
    public SubjectIdentity resetPassword(EncodedPassword replacement) {
        return new SubjectIdentity(
                id,
                loginName,
                Objects.requireNonNull(replacement, "新主体凭据摘要不能为空"),
                status,
                version + 1L,
                createdAt);
    }

    @Override
    public SubjectId id() {
        return id;
    }

    public String loginName() {
        return loginName;
    }

    public EncodedPassword encodedPassword() {
        return encodedPassword;
    }

    public SubjectIdentityStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == SubjectIdentityStatus.ACTIVE;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireLoginName(String value) {
        Objects.requireNonNull(value, "全局登录名不能为空");
        if (!LOGIN_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("全局登录名格式不正确");
        }
        return value;
    }
}
