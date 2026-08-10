package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 角色绑定写入所需的受锁成员事实与原子提交端口。
 */
public interface TenantRoleBindingStore {

    Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId);

    boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantBuiltInRole role);

    void commit(TenantRoleBindingResult result);
}
