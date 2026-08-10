package com.aiplatform.foundation.organizationaccess.application.authorization.port.out;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 自定义角色绑定所需的受锁事实和原子提交端口。 */
public interface TenantCustomRoleBindingStore {

    /** 锁定当前租户内已启用的成员资格。 */
    Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId);

    /** 锁定当前租户内仍可绑定的自定义角色。 */
    Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId);

    /** 判断成员是否已经拥有指定自定义角色。 */
    boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantCustomRoleId roleId);

    /** 同一事务提交成员权限修订和不可变绑定事实。 */
    void commit(TenantCustomRoleBindingResult result);
}
