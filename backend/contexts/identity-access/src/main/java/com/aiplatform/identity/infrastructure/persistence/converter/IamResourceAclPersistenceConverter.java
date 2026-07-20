package com.aiplatform.identity.infrastructure.persistence.converter;

import com.aiplatform.identity.domain.permission.ResourceAcl;
import com.aiplatform.identity.domain.permission.ResourceScope;
import com.aiplatform.identity.infrastructure.persistence.entity.IamResourceAclPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 资源 ACL 持久化投影转换器，避免授权适配器直接拼装领域对象。
 */
@Component
public class IamResourceAclPersistenceConverter {
    public ResourceAcl toDomain(IamResourceAclPersistenceEntity row) {
        Objects.requireNonNull(row, "资源 ACL 行不能为空");
        return new ResourceAcl(
                TenantId.of(requireLong(row.getTenantId(), "资源 ACL 租户 ID")),
                requireLong(row.getId(), "资源 ACL ID"),
                new ResourceScope(
                        requireText(row.getResourceType(), "资源类型"),
                        requireLong(row.getResourceId(), "资源 ID")
                ),
                requireText(row.getSubjectType(), "授权主体类型"),
                requireLong(row.getSubjectId(), "授权主体 ID"),
                new PermissionCode(requireText(row.getPermissionCode(), "权限编码")),
                requireText(row.getEffect(), "授权效果"),
                row.getExpiresAt(),
                true
        );
    }

    private static String requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
