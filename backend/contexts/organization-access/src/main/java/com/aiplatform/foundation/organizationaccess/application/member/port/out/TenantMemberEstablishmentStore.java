package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 建立成员资格所需的受锁主体事实、同租户唯一性与原子写入端口。 */
public interface TenantMemberEstablishmentStore {

    /** 锁定并确认全局主体仍处于启用状态。 */
    Optional<SubjectId> lockActiveSubject(SubjectId subjectId);

    /** 判断该启用主体是否已属于当前租户。 */
    boolean hasMembership(TenantId tenantId, SubjectId subjectId);

    /** 原子写入已由领域服务创建的成员资格及其普通用户固定角色。 */
    void insert(TenantMembership membership);
}
