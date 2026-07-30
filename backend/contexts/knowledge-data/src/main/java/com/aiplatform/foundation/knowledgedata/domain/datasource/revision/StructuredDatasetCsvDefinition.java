package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 结构化数据集修订冻结的私有 CSV、Schema 与字段映射。
 *
 * <p>主内容字段和可选标题字段必须是允许检索的文本字段。受限字段不能进入 M3 的规范化文本片段；行过滤只接受
 * 固定枚举，禁止把 SQL、任意表达式或原始敏感字段值写入修订。</p>
 */
public record StructuredDatasetCsvDefinition(
        StructuredDatasetCsvReference csv,
        String mappingVersion,
        String contentField,
        String titleField,
        List<StructuredDatasetFieldMapping> fields,
        StructuredDatasetRowFilter rowFilter) implements ValueObject {

    private static final Pattern MAPPING_VERSION = Pattern.compile("[a-z][a-z0-9-]{2,63}");

    public StructuredDatasetCsvDefinition {
        csv = Objects.requireNonNull(csv, "私有 CSV 对象不能为空");
        mappingVersion = requireMappingVersion(mappingVersion);
        contentField = requireFieldReference(contentField, "主内容字段不能为空");
        titleField = titleField == null ? null : requireFieldReference(titleField, "标题字段格式不正确");
        if (contentField.equals(titleField)) {
            throw new IllegalArgumentException("标题字段不能与主内容字段相同");
        }
        fields = List.copyOf(Objects.requireNonNull(fields, "字段映射不能为空"));
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("字段映射不能为空");
        }
        var byName = fieldsByName(fields);
        requireSearchableTextField(byName, contentField, "主内容字段必须映射为允许检索的文本字段");
        if (titleField != null) {
            requireSearchableTextField(byName, titleField, "标题字段必须映射为允许检索的文本字段");
        }
        rowFilter = Objects.requireNonNull(rowFilter, "行过滤策略不能为空");
    }

    /** 返回覆盖对象、字段映射、行过滤和映射版本的稳定定义摘要。 */
    public String definitionDigest() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, csv.objectKey());
            update(digest, csv.contentDigest());
            update(digest, Long.toString(csv.byteSize()));
            update(digest, StructuredDatasetCsvReference.UTF8_CSV);
            update(digest, mappingVersion);
            update(digest, contentField);
            update(digest, titleField == null ? "" : titleField);
            update(digest, rowFilter.name());
            for (var field : fields) {
                update(digest, field.fieldName());
                update(digest, field.logicalType().name());
                update(digest, field.sensitivityLevel().name());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static Map<String, StructuredDatasetFieldMapping> fieldsByName(
            List<StructuredDatasetFieldMapping> fields) {
        var byName = new HashMap<String, StructuredDatasetFieldMapping>();
        for (var field : fields) {
            var nonNullField = Objects.requireNonNull(field, "字段映射不能包含空值");
            if (byName.putIfAbsent(nonNullField.fieldName(), nonNullField) != null) {
                throw new IllegalArgumentException("字段映射不能包含重复字段名");
            }
        }
        return byName;
    }

    private static void requireSearchableTextField(
            Map<String, StructuredDatasetFieldMapping> fields,
            String fieldName,
            String message) {
        var field = fields.get(fieldName);
        if (field == null || field.logicalType() != StructuredDatasetLogicalType.TEXT
                || !field.sensitivityLevel().searchable()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireMappingVersion(String value) {
        Objects.requireNonNull(value, "字段映射版本不能为空");
        if (!MAPPING_VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("字段映射版本格式不正确");
        }
        return value;
    }

    private static String requireFieldReference(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank() || value.length() > 128 || value.indexOf('\u0000') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
