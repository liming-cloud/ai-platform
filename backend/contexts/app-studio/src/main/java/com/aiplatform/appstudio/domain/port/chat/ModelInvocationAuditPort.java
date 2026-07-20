package com.aiplatform.appstudio.domain.port.chat;

import com.aiplatform.appstudio.domain.port.chat.model.ModelInvocationAuditRecord;

/**
 * 模型调用审计端口：记录 App Studio 调用模型的成功/失败结果，并返回可挂到聊天消息上的日志 ID。
 */
public interface ModelInvocationAuditPort {
    String record(ModelInvocationAuditRecord record);
}
