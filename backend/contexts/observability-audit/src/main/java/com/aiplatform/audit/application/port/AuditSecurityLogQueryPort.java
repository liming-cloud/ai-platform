package com.aiplatform.audit.application.port;

import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import java.util.List;

/**
 * 安全审计日志查询端口，应用层只消费已隔离的查询结果。
 */
public interface AuditSecurityLogQueryPort {
    long countLogs(AuditSecurityLogQuery query);

    List<AuditSecurityLogResult> findLogs(AuditSecurityLogQuery query);
}
