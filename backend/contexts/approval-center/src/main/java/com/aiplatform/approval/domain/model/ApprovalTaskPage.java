package com.aiplatform.approval.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 数据库已完成过滤与分页后的审批任务快照。
 */
public record ApprovalTaskPage(List<ApprovalTask> items, long total, int page, int pageSize) {
    public ApprovalTaskPage {
        items = List.copyOf(Objects.requireNonNull(items, "审批任务分页数据不能为空"));
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
        if (total < 0) {
            throw new IllegalArgumentException("审批任务总数不能小于 0");
        }
        if (items.size() > pageSize) {
            throw new IllegalArgumentException("审批任务分页数据不能超过每页条数");
        }
    }
}
