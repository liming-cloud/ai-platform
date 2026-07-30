package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.request;

import java.util.List;

/** 知识管理员登记私有 CSV 数据集时提交的非敏感字段映射。 */
public record StructuredDatasetRegistrationRequest(
        String knowledgeSpaceId,
        String name,
        String mappingVersion,
        String contentField,
        String titleField,
        List<StructuredDatasetFieldRequest> fields,
        String rowFilter) {
}
