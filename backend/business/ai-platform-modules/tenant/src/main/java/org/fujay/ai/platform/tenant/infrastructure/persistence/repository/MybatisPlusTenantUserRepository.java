package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.model.UserCredential;
import org.fujay.ai.platform.tenant.domain.repository.TenantUserRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserCredentialMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserCredentialPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusTenantUserRepository implements TenantUserRepository {

    private final IamUserMapper userMapper;
    private final IamUserCredentialMapper credentialMapper;

    public MybatisPlusTenantUserRepository(IamUserMapper userMapper, IamUserCredentialMapper credentialMapper) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
    }

    @Override
    public Optional<TenantUser> findByUsername(Long tenantId, String username) {
        return Optional.ofNullable(userMapper.selectOne(Wrappers.<IamUserPO>lambdaQuery()
                        .eq(IamUserPO::getTenantId, tenantId)
                        .eq(IamUserPO::getUsername, username)))
                .map(TenantPersistenceConverter::toTenantUser);
    }

    @Override
    public Optional<TenantUser> findById(Long tenantId, Long userId) {
        return Optional.ofNullable(userMapper.selectOne(Wrappers.<IamUserPO>lambdaQuery()
                        .eq(IamUserPO::getTenantId, tenantId)
                        .eq(IamUserPO::getId, userId)))
                .map(TenantPersistenceConverter::toTenantUser);
    }

    @Override
    public Optional<UserCredential> findCredential(Long tenantId, Long userId) {
        return Optional.ofNullable(credentialMapper.selectOne(Wrappers.<IamUserCredentialPO>lambdaQuery()
                        .eq(IamUserCredentialPO::getTenantId, tenantId)
                        .eq(IamUserCredentialPO::getUserId, userId)))
                .map(TenantPersistenceConverter::toCredential);
    }

    @Override
    public TenantUser save(TenantUser user) {
        LocalDateTime now = LocalDateTime.now();
        IamUserPO po = userMapper.selectById(user.id());
        if (po == null) {
            po = new IamUserPO();
            po.setId(user.id());
            po.setTenantId(user.tenantId());
            po.setUsername(user.username());
            po.setCreatedAt(now);
        }
        po.setDisplayName(user.displayName());
        po.setStatus(user.status());
        po.setSystemAccount(user.systemAccount());
        po.setUpdatedAt(now);
        if (userMapper.selectById(po.getId()) == null) {
            userMapper.insert(po);
        } else {
            userMapper.updateById(po);
        }
        return TenantPersistenceConverter.toTenantUser(po);
    }

    @Override
    public UserCredential saveCredential(UserCredential credential) {
        IamUserCredentialPO po = credentialMapper.selectById(credential.id());
        if (po == null) {
            po = new IamUserCredentialPO();
            po.setId(credential.id());
            po.setTenantId(credential.tenantId());
            po.setUserId(credential.userId());
        }
        po.setPasswordHash(credential.passwordHash());
        po.setUpdatedAt(LocalDateTime.now());
        if (credentialMapper.selectById(po.getId()) == null) {
            credentialMapper.insert(po);
        } else {
            credentialMapper.updateById(po);
        }
        return TenantPersistenceConverter.toCredential(po);
    }
}
