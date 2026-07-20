package com.aiplatform.approval.application.result;

import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import java.util.List;
import java.util.Objects;

/**
 * 审批任务分页用例结果，保留数据库总数而不在 JVM 中重新分页。
 */
public record ApprovalTaskPageResult(
        List<ApprovalTaskResult> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {
    public ApprovalTaskPageResult {
        items = List.copyOf(Objects.requireNonNull(items, "审批任务分页结果不能为空"));
        if (page < 1 || pageSize < 1 || pageSize > 100 || total < 0) {
            throw new IllegalArgumentException("审批任务分页结果不合法");
        }
        int expectedPages = total == 0 ? 0 : Math.toIntExact(((total - 1L) / pageSize) + 1L);
        if (totalPages != expectedPages) {
            throw new IllegalArgumentException("审批任务总页数与总条数不一致");
        }
    }

    public static ApprovalTaskPageResult from(ApprovalTaskPage page) {
        Objects.requireNonNull(page, "审批任务分页不能为空");
        List<ApprovalTaskResult> items = page.items().stream().map(ApprovalTaskResult::from).toList();
        int totalPages = page.total() == 0
                ? 0
                : Math.toIntExact(((page.total() - 1L) / page.pageSize()) + 1L);
        return new ApprovalTaskPageResult(items, page.total(), page.page(), page.pageSize(), totalPages);
    }
}
