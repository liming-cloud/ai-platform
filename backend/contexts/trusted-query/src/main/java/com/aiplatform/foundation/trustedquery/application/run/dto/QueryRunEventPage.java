package com.aiplatform.foundation.trustedquery.application.run.dto;

import java.util.List;
import java.util.Objects;

/**
 * 单次事件重放结果及运行终态信息。
 */
public record QueryRunEventPage(
        List<QueryRunEventView> items,
        long resumeAfterSequence,
        boolean terminal) {

    public QueryRunEventPage {
        items = List.copyOf(Objects.requireNonNull(items, "查询事件不能为空"));
        if (resumeAfterSequence < 0) {
            throw new IllegalArgumentException("事件恢复序号不能为负数");
        }
        long previous = 0;
        for (var item : items) {
            if (item.sequence() <= previous
                    || item.sequence() > resumeAfterSequence) {
                throw new IllegalArgumentException("查询事件必须按序排列");
            }
            previous = item.sequence();
        }
    }
}
