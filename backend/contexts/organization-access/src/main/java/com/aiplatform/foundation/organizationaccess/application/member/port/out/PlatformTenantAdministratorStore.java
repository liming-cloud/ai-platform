package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantAdministratorGovernance;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 平台治理租户管理员所需的共同主体锁、成员事实和原子写入端口。 */
public interface PlatformTenantAdministratorStore {

    /** 锁定目标租户的权威生命周期根，串行化该租户的管理员状态变更。 */
    void lockTenantGovernanceScope(TenantId tenantId);

    /** 锁定活动候选主体，作为平台／租户人员范围转换的共同排序点。 */
    Optional<SubjectIdentity> lockActiveSubject(SubjectId subjectId);

    /** 由目标成员定位并锁定关联主体；主体活动性由具体用例决定。 */
    Optional<SubjectIdentity> lockSubjectForMembership(TenantId tenantId, MemberId membershipId);

    /** 判断受锁主体是否仍拥有活动平台管理员绑定。 */
    boolean hasActivePlatformAdministrator(SubjectId subjectId);

    /** 在主体锁内检查其他租户已有的管理员资格，停用资格仍占用其所属租户。 */
    boolean hasAdministratorInOtherTenant(SubjectId subjectId, TenantId tenantId);

    /** 锁定目标主体在租户内的既有成员资格。 */
    Optional<TenantMembership> lockMembershipBySubject(TenantId tenantId, SubjectId subjectId);

    /** 锁定目标租户管理员成员资格。 */
    Optional<TenantMembership> lockMembership(TenantId tenantId, MemberId membershipId);

    /** 计算排除目标后的其他启用租户管理员数量。 */
    long countOtherEnabledAdministrators(TenantId tenantId, MemberId membershipId);

    /** 新建或提升成员并收敛为唯一租户管理员角色绑定。 */
    void saveAssignment(TenantAdministratorGovernance.Assignment result);

    /** 在同一事务中保存新管理员并停用旧管理员。 */
    void saveReplacement(TenantAdministratorGovernance.Replacement result);

    /** 更新管理员成员资格状态、角色和修订。 */
    void update(TenantMembership membership);
}
