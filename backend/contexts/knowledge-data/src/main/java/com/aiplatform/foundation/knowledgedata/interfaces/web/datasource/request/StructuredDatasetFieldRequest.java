package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request;

/** 结构化数据集字段映射的公开输入。 */
public record StructuredDatasetFieldRequest(String fieldName, String logicalType, String sensitivityLevel) {
}
