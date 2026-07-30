package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

/**
 * 结构化数据集修订冻结的行过滤策略。
 *
 * <p>M3 不接受任意表达式、SQL 或原始字段值；后续增加受控筛选语义时必须新增枚举值和相应 ADR。</p>
 */
public enum StructuredDatasetRowFilter {
    ALL_ROWS,
    NON_BLANK_CONTENT
}
