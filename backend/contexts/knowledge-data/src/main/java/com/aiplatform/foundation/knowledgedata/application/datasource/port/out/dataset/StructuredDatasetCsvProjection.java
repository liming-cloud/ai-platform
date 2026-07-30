package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

/** 结构化数据集 CSV 投影后的受限事实；不包含原始行或未映射字段。 */
public record StructuredDatasetCsvProjection(
        String normalizedText, long recordCount, long skippedBlankRowCount) {
    public StructuredDatasetCsvProjection {
        if (normalizedText == null || normalizedText.isBlank() || recordCount < 1 || skippedBlankRowCount < 0) {
            throw new IllegalArgumentException("结构化数据集投影结果不合法");
        }
    }
}
