package com.aiplatform.appstudio.application.result;

import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import java.util.Objects;

public record DebugEvidenceResult(
        AppStudioDebugRunDetail detail,
        boolean masked,
        String auditEventId
) {
    public DebugEvidenceResult {
        Objects.requireNonNull(detail, "调试运行详情不能为空");
        if (!masked && (auditEventId == null || auditEventId.isBlank())) {
            throw new IllegalArgumentException("完整调试证据必须关联审计事件");
        }
        auditEventId = auditEventId == null || auditEventId.isBlank() ? null : auditEventId.trim();
    }
}
