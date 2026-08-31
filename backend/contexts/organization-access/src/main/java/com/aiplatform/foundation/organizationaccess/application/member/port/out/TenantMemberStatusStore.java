package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 租户成员状态变更所需的受锁目录事实与原子写入端口。 */
public interface TenantMemberStatusStore {

    /** 由当前租户成员定位并锁定共同人员主体，确保跨范围激活遵守统一锁顺序。 */
    Optional<SubjectId> lockSubjectForMembership(TenantId tenantId, MemberId membershipId);

    /** 判断受锁主体是否仍拥有活动平台管理员绑定。 */
    boolean hasActivePlatformAdministrator(SubjectId subjectId);

    /** 锁定当前租户内的目标成员资格。 */
    Optional<TenantMembership> lock(TenantId tenantId, MemberId membershipId);

    /** 判断目标成员当前是否拥有有效的访问管理权限。 */
    boolean hasAccessManage(TenantId tenantId, MemberId membershipId);

    /** 返回排除目标成员后的其他已启用访问管理员数量。 */
    long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId);

    /** 原子更新成员状态、权限修订和乐观版本。 */
    void update(TenantMembership membership);
}
