package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/** 结构化数据集修订冻结字段映射的持久化实体。 */
@TableName("knowledge_data.source_revision_dataset_field")
public class StructuredDatasetFieldEntity {

    @TableField("tenant_id") private Long tenantId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("data_source_id") private Long dataSourceId;
    private long revision;
    @TableField("field_ordinal") private int fieldOrdinal;
    @TableField("field_name") private String fieldName;
    @TableField("logical_type") private String logicalType;
    @TableField("sensitivity_level") private String sensitivityLevel;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long knowledgeSpaceId) { this.knowledgeSpaceId = knowledgeSpaceId; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public int getFieldOrdinal() { return fieldOrdinal; }
    public void setFieldOrdinal(int fieldOrdinal) { this.fieldOrdinal = fieldOrdinal; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getLogicalType() { return logicalType; }
    public void setLogicalType(String logicalType) { this.logicalType = logicalType; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
}
