package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredDatasetSourceRevisionTest {

    private static final TenantId TENANT = new TenantId(101L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(202L);
    private static final CreatorId CREATOR = new CreatorId(303L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void 结构化数据集修订冻结私有CSV和字段映射() {
        var definition = definition();

        var revision = SourceRevision.structuredDataset(
                source(GovernedDataSourceType.STRUCTURED_DATASET), definition, CREATOR, CREATED_AT);

        assertEquals(definition, revision.structuredDataset());
        assertEquals(definition.definitionDigest(), revision.definitionDigest());
        assertEquals("content", definition.contentField());
        assertEquals("title", definition.titleField());
    }

    @Test
    void 结构化数据集拒绝重复字段和非文本内容字段() {
        assertThrows(IllegalArgumentException.class, () -> new StructuredDatasetCsvDefinition(
                csv(),
                "csv-mapping-v1",
                "content",
                null,
                List.of(
                        field("content", StructuredDatasetLogicalType.TEXT),
                        field("content", StructuredDatasetLogicalType.TEXT)),
                StructuredDatasetRowFilter.NON_BLANK_CONTENT));

        assertThrows(IllegalArgumentException.class, () -> new StructuredDatasetCsvDefinition(
                csv(),
                "csv-mapping-v1",
                "content",
                null,
                List.of(field("content", StructuredDatasetLogicalType.NUMBER)),
                StructuredDatasetRowFilter.NON_BLANK_CONTENT));
    }

    @Test
    void 结构化数据集拒绝以受限字段作为可检索内容() {
        assertThrows(IllegalArgumentException.class, () -> new StructuredDatasetCsvDefinition(
                csv(),
                "csv-mapping-v1",
                "content",
                null,
                List.of(new StructuredDatasetFieldMapping(
                        "content",
                        StructuredDatasetLogicalType.TEXT,
                        StructuredDatasetSensitivityLevel.RESTRICTED)),
                StructuredDatasetRowFilter.NON_BLANK_CONTENT));
    }

    @Test
    void 结构化数据集定义只能绑定相同类型来源() {
        assertThrows(IllegalArgumentException.class, () -> SourceRevision.structuredDataset(
                source(GovernedDataSourceType.FILE), definition(), CREATOR, CREATED_AT));
    }

    private static StructuredDatasetCsvDefinition definition() {
        return new StructuredDatasetCsvDefinition(
                csv(),
                "csv-mapping-v1",
                "content",
                "title",
                List.of(
                        field("content", StructuredDatasetLogicalType.TEXT),
                        field("title", StructuredDatasetLogicalType.TEXT),
                        new StructuredDatasetFieldMapping(
                                "classification",
                                StructuredDatasetLogicalType.TEXT,
                                StructuredDatasetSensitivityLevel.INTERNAL)),
                StructuredDatasetRowFilter.NON_BLANK_CONTENT);
    }

    private static StructuredDatasetCsvReference csv() {
        return new StructuredDatasetCsvReference(
                "foundation-v1/tenants/101/knowledge-data/datasets/404/1/data.csv",
                "a".repeat(64),
                128);
    }

    private static StructuredDatasetFieldMapping field(String name, StructuredDatasetLogicalType type) {
        return new StructuredDatasetFieldMapping(name, type, StructuredDatasetSensitivityLevel.PUBLIC);
    }

    private static GovernedDataSource source(GovernedDataSourceType type) {
        return GovernedDataSource.create(
                TENANT,
                SPACE,
                new DataSourceId(404L),
                type,
                "受治理结构化数据集",
                CREATOR,
                CREATED_AT);
    }
}
