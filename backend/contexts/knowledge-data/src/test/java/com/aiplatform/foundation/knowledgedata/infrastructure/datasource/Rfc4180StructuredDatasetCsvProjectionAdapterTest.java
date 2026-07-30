package com.aiplatform.foundation.knowledgedata.infrastructure.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetFieldMapping;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetLogicalType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetSensitivityLevel;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class Rfc4180StructuredDatasetCsvProjectionAdapterTest {

    @Test
    void 应按冻结字段顺序投影标题和内容并以空行分隔记录() {
        var csv = "content,title,ignored\r\n  第一行  , 标题一 ,不应出现\r\n\"第二\r\n行\",标题二,秘密\r\n";
        var adapter = new Rfc4180StructuredDatasetCsvProjectionAdapter(
                storage(csv), 4096, 10, 4096, 1024);

        var projection = adapter.project(definition(StructuredDatasetRowFilter.ALL_ROWS));

        assertEquals("标题一\n第一行\n\n标题二\n第二\n行", projection.normalizedText());
        assertEquals(2, projection.recordCount());
        assertEquals(0, projection.skippedBlankRowCount());
    }

    private static StructuredDatasetCsvStoragePort storage(String csv) {
        return new StructuredDatasetCsvStoragePort() {
            @Override public StructuredDatasetCsvReference put(com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest request) { throw new UnsupportedOperationException(); }
            @Override public java.io.InputStream read(StructuredDatasetCsvReference reference) { return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)); }
            @Override public void delete(StructuredDatasetCsvReference reference) { throw new UnsupportedOperationException(); }
        };
    }

    private static StructuredDatasetCsvDefinition definition(StructuredDatasetRowFilter filter) {
        return new StructuredDatasetCsvDefinition(
                new StructuredDatasetCsvReference("private/dataset.csv", "a".repeat(64), 200),
                "csv-mapping-v1", "content", "title", List.of(
                        new StructuredDatasetFieldMapping("content", StructuredDatasetLogicalType.TEXT, StructuredDatasetSensitivityLevel.PUBLIC),
                        new StructuredDatasetFieldMapping("title", StructuredDatasetLogicalType.TEXT, StructuredDatasetSensitivityLevel.PUBLIC),
                        new StructuredDatasetFieldMapping("ignored", StructuredDatasetLogicalType.TEXT, StructuredDatasetSensitivityLevel.PUBLIC)), filter);
    }
}
