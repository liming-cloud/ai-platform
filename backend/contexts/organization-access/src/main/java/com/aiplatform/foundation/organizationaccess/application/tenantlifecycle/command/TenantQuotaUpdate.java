package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command;

/** 平台配额独立保存命令；版本由服务端进行乐观并发校验。 */
public record TenantQuotaUpdate(
        int knowledgeBases,
        int documentsPerKnowledgeBase,
        long bytesPerKnowledgeBase,
        long maxFileBytes,
        long maxTextBytes,
        long expectedVersion) {

    public TenantQuotaUpdate {
        if (expectedVersion < 0 || expectedVersion == Long.MAX_VALUE) {
            throw new IllegalArgumentException("配额预期版本不正确");
        }
    }
}
