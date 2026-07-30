package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/**
 * CSV 标题字段到受控逻辑类型与敏感级别的不可变映射。
 *
 * @throws IllegalArgumentException 字段名为空、包含控制字符或超过长度上限时拒绝创建。
 */
public record StructuredDatasetFieldMapping(
        String fieldName,
        StructuredDatasetLogicalType logicalType,
        StructuredDatasetSensitivityLevel sensitivityLevel) implements ValueObject {

    public StructuredDatasetFieldMapping {
        fieldName = requireFieldName(fieldName);
        logicalType = Objects.requireNonNull(logicalType, "字段逻辑类型不能为空");
        sensitivityLevel = Objects.requireNonNull(sensitivityLevel, "字段敏感级别不能为空");
    }

    private static String requireFieldName(String value) {
        Objects.requireNonNull(value, "字段名不能为空");
        if (value.isBlank() || value.length() > 128 || value.indexOf('\u0000') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("字段名格式不正确");
        }
        return value;
    }
}
