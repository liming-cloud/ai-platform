package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 租户独立资源配额的不可变版本快照；配置本身不代表接入预留或实际用量。
 * 容量使用整数字节，单文件上限不得超过单库容量。
 */
public record TenantQuota(
        TenantId tenantId,
        int knowledgeBases,
        int documentsPerKnowledgeBase,
        long bytesPerKnowledgeBase,
        long maxFileBytes,
        long maxTextBytes,
        long version) implements ValueObject {

    /**
     * 创建或恢复经过边界校验的配额快照。
     *
     * @throws NullPointerException 租户标识缺失时拒绝
     * @throws IllegalArgumentException 数量或容量非正、版本为负或单文件超过库容量时拒绝
     */
    public TenantQuota {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (knowledgeBases < 1 || documentsPerKnowledgeBase < 1
                || bytesPerKnowledgeBase < 1 || maxFileBytes < 1 || maxTextBytes < 1) {
            throw new IllegalArgumentException("资源配额必须为正整数");
        }
        if (maxFileBytes > bytesPerKnowledgeBase) {
            throw new IllegalArgumentException("单文件大小不能超过每库内容容量");
        }
        if (version < 0) {
            throw new IllegalArgumentException("配额版本不能为负数");
        }
    }
}
