package com.aiplatform.identity.infrastructure.persistence.converter;

import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionType;
import com.aiplatform.identity.infrastructure.persistence.entity.DelegatedTenantActionPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 平台代管租户动作持久化转换器。
 */
@Component
public class DelegatedTenantActionPersistenceConverter {
    public DelegatedTenantAction toDomain(DelegatedTenantActionPersistenceEntity row) {
        Objects.requireNonNull(row, "平台代管租户动作行不能为空");
        return new DelegatedTenantAction(
                requireLong(row.getId(), "代管动作 ID"),
                TenantId.of(requireLong(row.getTenantId(), "被代管租户 ID")),
                requireLong(row.getOperatorId(), "代管操作者 ID"),
                DelegatedTenantActionType.from(row.getActionType()),
                row.getTargetResourceType(),
                row.getTargetResourceId(),
                row.getReason(),
                row.getRequestHash(),
                row.getAuditEventId(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getVersion() == null ? 0 : row.getVersion()
        );
    }

    private static String requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }
}
