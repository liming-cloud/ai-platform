package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.knowledgedata.domain.model.TextDataSource;
import java.util.Objects;

/**
 * 文本数据源事务变更或历史重放结果。
 */
public record TextDataSourceMutationOutcome(
        TextDataSource source,
        boolean replayed) {

    public TextDataSourceMutationOutcome {
        Objects.requireNonNull(source, "文本数据源不能为空");
    }
}
