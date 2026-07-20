package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.port.EnterpriseUserRepository;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamUserMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 企业用户仓储的 MyBatis-Plus 实现，所有查询都显式携带 tenant_id 和 deleted_at 条件。
 */
@Repository
public class IamUserRepositoryAdapter implements EnterpriseUserRepository {
    private final IamUserMapper mapper;

    public IamUserRepositoryAdapter(IamUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<EnterpriseUser> findByLoginName(TenantId tenantId, String loginName) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        IamUserPersistenceEntity row = mapper.findActiveTenantUserByLoginName(tenantDatabaseId, loginName);
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public Optional<EnterpriseUser> findById(TenantId tenantId, String userId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        Long userDatabaseId = parseRequiredLong(userId, "用户 ID");
        IamUserPersistenceEntity row = mapper.findActiveTenantUserById(tenantDatabaseId, userDatabaseId);
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public EnterpriseUser save(EnterpriseUser user) {
        IamUserPersistenceEntity row = IamUserPersistenceEntity.fromDomain(user);
        int updated = mapper.update(row, activeUserUpdate(row));
        if (updated != 1) {
            throw new IllegalStateException("企业用户保存失败：记录不存在或已被删除");
        }
        return toDomain(row);
    }

    private EnterpriseUser toDomain(IamUserPersistenceEntity row) {
        Set<String> roleCodes = mapper.findActiveRoleCodes(row.getTenantId(), row.getId());
        return row.toDomain(roleCodes);
    }

    private LambdaUpdateWrapper<IamUserPersistenceEntity> activeUserUpdate(IamUserPersistenceEntity row) {
        return new LambdaUpdateWrapper<IamUserPersistenceEntity>()
                .eq(IamUserPersistenceEntity::getTenantId, row.getTenantId())
                .eq(IamUserPersistenceEntity::getId, row.getId())
                .isNull(IamUserPersistenceEntity::getDeletedAt);
    }

    private static Long parseTenantId(TenantId tenantId) {
        return parseRequiredLong(tenantId.value(), "租户 ID");
    }
}
