package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SourceRevisionTest {

    private static final TenantId TENANT = new TenantId(101L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(202L);
    private static final CreatorId CREATOR = new CreatorId(303L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-29T02:00:00Z");

    @Test
    void 文件修订冻结平台对象事实且来源从首个修订开始() {
        var source = source(GovernedDataSourceType.FILE);
        var file = new FileSourceObjectReference(
                "foundation-v1/tenants/101/knowledge-data/files/404/1/content.txt",
                "a".repeat(64),
                12,
                "text/plain; charset=utf-8");

        var revision = SourceRevision.file(source, file, CREATOR, CREATED_AT);

        assertEquals(1, source.currentRevision());
        assertEquals(1, revision.revision());
        assertEquals(file, revision.fileObject());
        assertEquals(file.definitionDigest(), revision.definitionDigest());
    }

    @Test
    void 文件对象只能绑定文件来源() {
        var file = new FileSourceObjectReference(
                "foundation-v1/tenants/101/knowledge-data/files/404/1/content.txt",
                "a".repeat(64),
                12,
                "text/plain; charset=utf-8");

        assertThrows(IllegalArgumentException.class, () -> SourceRevision.file(
                source(GovernedDataSourceType.OBJECT_STORAGE), file, CREATOR, CREATED_AT));
    }

    private static GovernedDataSource source(GovernedDataSourceType type) {
        return GovernedDataSource.create(
                TENANT,
                SPACE,
                new DataSourceId(404L),
                type,
                "受治理文件",
                CREATOR,
                CREATED_AT);
    }
}
