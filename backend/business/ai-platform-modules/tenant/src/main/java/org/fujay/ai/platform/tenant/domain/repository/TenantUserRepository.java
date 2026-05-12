package org.fujay.ai.platform.tenant.domain.repository;

import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.model.UserCredential;

public interface TenantUserRepository {

    Optional<TenantUser> findByUsername(Long tenantId, String username);

    Optional<TenantUser> findById(Long tenantId, Long userId);

    Optional<UserCredential> findCredential(Long tenantId, Long userId);

    TenantUser save(TenantUser user);

    UserCredential saveCredential(UserCredential credential);
}
