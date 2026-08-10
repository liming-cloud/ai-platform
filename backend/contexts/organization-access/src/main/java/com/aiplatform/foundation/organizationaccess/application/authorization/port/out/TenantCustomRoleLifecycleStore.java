package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleLifecycleResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Optional;

/** 自定义角色修改或停用所需的受锁事实与同事务提交端口。 */
public interface TenantCustomRoleLifecycleStore {

    Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId);

    List<TenantMembership> lockEnabledBoundMembers(TenantId tenantId, TenantCustomRoleId roleId);

    long countEnabledAccessManagersAfterChange(
            TenantId tenantId, TenantCustomRoleId roleId, boolean retainsAccessManage);

    void commit(TenantCustomRoleLifecycleResult result);
}
