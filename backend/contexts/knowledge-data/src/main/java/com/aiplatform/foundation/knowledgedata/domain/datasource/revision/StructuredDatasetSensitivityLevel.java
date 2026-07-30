package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

/** 结构化数据集字段在同步、质量和检索前必须执行的敏感级别。 */
public enum StructuredDatasetSensitivityLevel {
    PUBLIC(true),
    INTERNAL(true),
    CONFIDENTIAL(false),
    RESTRICTED(false);

    private final boolean searchable;

    StructuredDatasetSensitivityLevel(boolean searchable) {
        this.searchable = searchable;
    }

    /** 返回该字段能否作为 M3 首版的可检索文本。 */
    public boolean searchable() {
        return searchable;
    }
}
