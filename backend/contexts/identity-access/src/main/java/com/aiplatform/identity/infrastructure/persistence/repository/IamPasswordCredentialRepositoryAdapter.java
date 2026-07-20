package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.identity.domain.port.PasswordCredentialRepository;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserCredentialPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamUserMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 密码凭证仓储适配器，查询限定 tenant_id、user_id 和未删除用户。
 */
@Repository
public class IamPasswordCredentialRepositoryAdapter implements PasswordCredentialRepository {
    private final IamUserMapper mapper;

    public IamPasswordCredentialRepositoryAdapter(IamUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PasswordCredential> findByUserId(TenantId tenantId, String userId) {
        Long tenantDatabaseId = parseRequiredLong(tenantId.value(), "tenantId");
        Long userDatabaseId = parseRequiredLong(userId, "userId");
        IamUserCredentialPersistenceEntity row = mapper.findActiveCredential(tenantDatabaseId, userDatabaseId);
        try {
            return Optional.ofNullable(row).map(IamUserCredentialPersistenceEntity::toDomain);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }
}
