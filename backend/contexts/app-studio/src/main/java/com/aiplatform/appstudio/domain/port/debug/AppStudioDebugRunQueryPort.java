package com.aiplatform.appstudio.domain.port.debug;

import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunQueryFilter;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSummary;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 调试运行查询端口：提供历史列表与详情回放。
 */
public interface AppStudioDebugRunQueryPort {
    List<AppStudioDebugRunSummary> listDebugRuns(AppStudioDebugRunQueryFilter filter);

    Optional<AppStudioDebugRunDetail> findDebugRunDetail(TenantId tenantId, String applicationId, String userId, String assistantMessageId);
}
