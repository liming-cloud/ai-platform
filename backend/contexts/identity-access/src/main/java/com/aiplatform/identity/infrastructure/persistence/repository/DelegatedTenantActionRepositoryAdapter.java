package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;
import com.aiplatform.identity.domain.port.DelegatedTenantActionRepository;
import com.aiplatform.identity.infrastructure.persistence.converter.DelegatedTenantActionPersistenceConverter;
import com.aiplatform.identity.infrastructure.persistence.entity.DelegatedTenantActionPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.DelegatedTenantActionMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 平台代管租户动作仓储适配器。
 */
@Repository
public class DelegatedTenantActionRepositoryAdapter implements DelegatedTenantActionRepository {
    private final DelegatedTenantActionMapper mapper;
    private final DelegatedTenantActionPersistenceConverter converter;

    public DelegatedTenantActionRepositoryAdapter(
            DelegatedTenantActionMapper mapper,
            DelegatedTenantActionPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "平台代管租户动作 Mapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "平台代管租户动作转换器不能为空");
    }

    @Override
    public Optional<DelegatedTenantAction> findByRequestHash(
            TenantId tenantId,
            String operatorId,
            String requestHash
    ) {
        return Optional.ofNullable(mapper.findByRequestHash(
                        parseRequiredLong(tenantId.value(), "被代管租户 ID"),
                        parseRequiredLong(operatorId, "代管操作者 ID"),
                        requestHash
                ))
                .map(converter::toDomain);
    }

    @Override
    public DelegatedTenantAction save(DelegatedTenantAction action) {
        Objects.requireNonNull(action, "平台代管租户动作不能为空");
        DelegatedTenantActionPersistenceEntity row = new DelegatedTenantActionPersistenceEntity();
        row.setTenantId(parseRequiredLong(action.tenantId().value(), "被代管租户 ID"));
        row.setOperatorId(parseRequiredLong(action.operatorId(), "代管操作者 ID"));
        row.setActionType(action.actionType().name());
        row.setTargetResourceType(action.targetResourceType());
        row.setTargetResourceId(action.targetResourceId());
        row.setReason(action.reason());
        row.setRequestHash(action.requestHash());
        row.setAuditEventId(action.auditEventId());
        row.setStatus(action.status());
        Long actionId = mapper.insertAction(row);
        if (actionId == null) {
            return findByRequestHash(action.tenantId(), action.operatorId(), action.requestHash())
                    .orElseThrow(() -> new IllegalStateException("平台代管租户动作幂等冲突后未找到既有记录"));
        }
        return action.withActionId(String.valueOf(actionId));
    }
}
