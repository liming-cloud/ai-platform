package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.identity.domain.PasswordHash;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * `iam_user` 表中的认证凭证投影，只映射登录校验所需的敏感字段。
 */
public class IamUserCredentialPersistenceEntity {
    private Long id;
    private Long tenantId;
    private String passwordHash;
    private Instant updatedAt;

    public PasswordCredential toDomain() {
        return new PasswordCredential(
                TenantId.of(requireLong(tenantId, "密码凭证租户 ID")),
                requireLong(id, "密码凭证用户 ID"),
                PasswordHash.of(passwordHash),
                updatedAt
        );
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
