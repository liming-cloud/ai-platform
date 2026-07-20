package com.aiplatform.identity.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * `iam_user` 表持久化实体，字段与数据库设计保持一一映射。
 */
@TableName("iam_user")
public class IamUserPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("department_id")
    private Long departmentId;

    @TableField("username")
    private String username;

    @TableField("display_name")
    private String displayName;

    @TableField("email")
    private String email;

    @TableField("mobile")
    private String mobile;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("status")
    private String status;

    @TableField("last_login_at")
    private Instant lastLoginAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("deleted_at")
    private Instant deletedAt;

    @TableField("version")
    private Integer version;

    public EnterpriseUser toDomain(Set<String> roleCodes) {
        return new EnterpriseUser(
                TenantId.of(requireLong(tenantId, "用户租户 ID")),
                requireLong(id, "用户 ID"),
                username,
                displayName,
                roleCodes,
                UserStatus.valueOf(status),
                createdAt,
                updatedAt
        );
    }

    public static IamUserPersistenceEntity fromDomain(EnterpriseUser user) {
        Objects.requireNonNull(user, "企业用户不能为空");
        IamUserPersistenceEntity entity = new IamUserPersistenceEntity();
        entity.setId(parseRequiredLong(user.userId(), "userId"));
        entity.setTenantId(parseRequiredLong(user.tenantId().value(), "tenantId"));
        entity.setUsername(user.loginName());
        entity.setDisplayName(user.displayName());
        entity.setStatus(user.status().name());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        return entity;
    }

    private static String requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
