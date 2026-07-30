package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/** 由知识管理员提交私有 UTF-8 CSV 及冻结字段映射的可信登记请求。 */
public record RegisterStructuredDatasetCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String name,
        String mappingVersion,
        String contentField,
        String titleField,
        List<StructuredDatasetFieldMapping> fields,
        StructuredDatasetRowFilter rowFilter,
        InputStream content) {

    public RegisterStructuredDatasetCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        fields = List.copyOf(Objects.requireNonNull(fields, "字段映射不能为空"));
        rowFilter = Objects.requireNonNull(rowFilter, "行过滤策略不能为空");
        content = Objects.requireNonNull(content, "CSV 内容不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank() || name == null || name.isBlank()
                || mappingVersion == null || mappingVersion.isBlank()
                || contentField == null || contentField.isBlank()) {
            throw new IllegalArgumentException("结构化数据集登记请求不完整");
        }
    }
}
