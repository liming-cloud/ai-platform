package com.aiplatform.shared.audit.port;

import com.aiplatform.shared.audit.model.AuditEvent;

/**
 * 审计事件发布端口，由业务上下文写入统一审计流水。
 */
public interface AuditEventPublisher {
    void publish(AuditEvent event);
}
