package com.aiplatform.foundation.trustedquery.application.run.dto;

import java.util.Objects;

/**
 * 查询创建结果及其幂等重放事实。
 */
public record CreateQueryRunResult(QueryRunView queryRun, boolean replayed) {

    public CreateQueryRunResult {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
    }
}
