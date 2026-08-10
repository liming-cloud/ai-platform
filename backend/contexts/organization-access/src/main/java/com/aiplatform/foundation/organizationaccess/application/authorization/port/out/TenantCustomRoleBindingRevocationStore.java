package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingRevocationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 自定义角色解绑所需的受锁事实与同事务提交端口。 */
public interface TenantCustomRoleBindingRevocationStore {

    Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId);

    Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId);

    boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantCustomRoleId roleId);

    boolean retainsAccessManageAfterRevocation(
            TenantId tenantId, MemberId membershipId, TenantCustomRoleId roleId);

    long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId);

    void commit(TenantCustomRoleBindingRevocationResult result);
}
