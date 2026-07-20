package com.aiplatform.approval.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * 审批任务列表查询命令，状态为空时返回当前租户全部有效审批任务。
 */
public record ListApprovalTasksCommand(
        TenantId tenantId,
        Optional<String> status,
        Optional<String> riskLevel,
        Optional<String> sourceContext,
        String keyword,
        int page,
        int pageSize
) {
    public ListApprovalTasksCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(status, "审批任务状态查询条件不能为空");
        Objects.requireNonNull(riskLevel, "审批任务风险查询条件不能为空");
        Objects.requireNonNull(sourceContext, "审批任务来源查询条件不能为空");
        Objects.requireNonNull(keyword, "审批任务关键词不能为空");
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
    }
}
