package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import java.util.Optional;

/**
 * 新租户建立所需的受锁主体读取与原子提交端口。
 */
public interface TenantProvisioningStore {

    /**
     * 锁定读取可作为初始管理员的已启用主体。
     */
    Optional<SubjectIdentity> lockActiveInitialAdministrator(SubjectId subjectId);

    /** 判断受锁初始管理员主体是否仍拥有活动平台管理员绑定。 */
    boolean hasActivePlatformAdministrator(SubjectId subjectId);

    /**
     * 原子保存领域服务产生的完整建立结果。
     */
    void commit(TenantProvisioningResult result);
}
