package com.aiplatform.approval.domain.model;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Locale;
import java.util.Objects;

/**
 * 审批任务的租户内数据库查询条件。所有分页边界在进入持久化 seam 前已固定。
 */
public record ApprovalTaskQuery(
        TenantId tenantId,
        ApprovalStatus status,
        ApprovalRiskLevel riskLevel,
        String sourceContext,
        String keyword,
        int page,
        int pageSize
) {
    public ApprovalTaskQuery {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        sourceContext = normalizeOptional(sourceContext, "来源上下文");
        keyword = Objects.requireNonNull(keyword, "审批任务关键词不能为空").trim().toLowerCase(Locale.ROOT);
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page - 1L, pageSize);
    }

    public String keywordPattern() {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String normalizeOptional(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
