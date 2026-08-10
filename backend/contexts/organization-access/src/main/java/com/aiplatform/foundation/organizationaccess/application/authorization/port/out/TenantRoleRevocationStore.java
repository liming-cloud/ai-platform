package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleRevocationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 撤销内置角色所需的受锁成员事实和原子提交端口。 */
public interface TenantRoleRevocationStore {

    Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId);

    boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantBuiltInRole role);

    /** 返回删除该内置角色绑定后目标成员是否仍拥有有效 `access:manage`。 */
    boolean retainsAccessManageAfterRevocation(
            TenantId tenantId, MemberId membershipId, TenantBuiltInRole role);

    /** 返回同租户内除目标外当前拥有有效 `access:manage` 的启用成员数量。 */
    long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId);

    /** 在同一事务内删除绑定并写入领域结果中的成员权限修订。 */
    void commit(TenantRoleRevocationResult result);
}
