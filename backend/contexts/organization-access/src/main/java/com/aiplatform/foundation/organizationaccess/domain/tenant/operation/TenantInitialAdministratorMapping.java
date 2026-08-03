package com.aiplatform.foundation.organizationaccess.domain.tenant.operation;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 由历史租户责任人确认的一条初始管理员归属事实。
 *
 * <p>该值只在受控部署事务内短暂存在，不是 HTTP、令牌、日志、版本库或应用配置契约。</p>
 */
public record TenantInitialAdministratorMapping(TenantId tenantId, SubjectId subjectId)
        implements ValueObject {

    public TenantInitialAdministratorMapping {
        tenantId = Objects.requireNonNull(tenantId, "历史租户标识不能为空");
        subjectId = Objects.requireNonNull(subjectId, "历史初始管理员主体不能为空");
    }
}
