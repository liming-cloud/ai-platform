package com.aiplatform.foundation.organizationaccess.application.member.port.out;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAccountFact;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 查询进程只读取当前成员事实，不获取身份写入、授权变更或审计端口。 */
public interface MemberAccessFacts {
    /** 当前租户不可用或资格不存在时返回空，不恢复历史授权。 */
    Optional<MemberAccountFact> find(TenantId tenantId, long membershipId);
}
