package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证旧同步产物升级后不会被伪造为具备冻结正文引用的 M3 产物。 */
@Testcontainers
class SyncArtifactContentReferenceMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Test
    void 历史同步产物升级后保留空冻结正文引用且不进入索引候选() throws Exception {
        flyway(MigrationVersion.fromVersion("202607300022")).migrate();
        try (var connection = connection(); var statement = connection.createStatement()) {
            insertHistoricalArtifact(statement);
        }

        flyway(null).migrate();

        try (var connection = connection(); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("""
                    SELECT content_version, content_object_key, content_byte_size,
                           content_media_type, content_created_at
                    FROM knowledge_data.sync_artifact
                    WHERE id = 3001
                    """)) {
                row.next();
                assertEquals(null, row.getObject("content_version"));
                assertEquals(null, row.getObject("content_object_key"));
                assertEquals(null, row.getObject("content_byte_size"));
                assertEquals(null, row.getObject("content_media_type"));
                assertEquals(null, row.getObject("content_created_at"));
            }
            try (var candidates = statement.executeQuery("""
                    SELECT count(*)
                    FROM knowledge_data.published_sync_artifact publication
                    JOIN knowledge_data.sync_artifact artifact
                      ON artifact.tenant_id = publication.tenant_id
                     AND artifact.knowledge_space_id = publication.knowledge_space_id
                     AND artifact.id = publication.artifact_id
                    WHERE publication.tenant_id = 1001
                      AND publication.knowledge_space_id = 2001
                      AND artifact.content_version IS NOT NULL
                    """)) {
                candidates.next();
                assertEquals(0L, candidates.getLong(1));
            }
            assertThrows(Exception.class, () -> statement.executeUpdate("""
                    UPDATE knowledge_data.sync_artifact
                    SET content_version = 1
                    WHERE id = 3001
                    """));
        }
    }

    private static Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/knowledge-data");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insertHistoricalArtifact(Statement statement) throws Exception {
        statement.executeUpdate("""
                INSERT INTO knowledge_data.knowledge_space
                    (tenant_id, id, name, description, status, version, created_by, created_at)
                VALUES (1001, 2001, '历史知识空间', NULL, 'DRAFT', 0, 9001, TIMESTAMPTZ '2026-07-30T00:00:00Z')
                """);
        statement.executeUpdate("""
                INSERT INTO knowledge_data.governed_data_source
                    (tenant_id, knowledge_space_id, id, source_type, name, status, version,
                     created_by, created_at, failed_operation, current_revision)
                VALUES (1001, 2001, 2002, 'FILE', '历史文件来源', 'READY', 1,
                        9001, TIMESTAMPTZ '2026-07-30T00:00:00Z', NULL, 1)
                """);
        statement.executeUpdate("""
                INSERT INTO knowledge_data.source_revision
                    (tenant_id, knowledge_space_id, data_source_id, revision, source_type,
                     definition_digest, created_by, created_at, object_key, content_digest,
                     byte_size, media_type, object_range_id, object_selection_revision,
                     object_collection_digest, dataset_mapping_version, dataset_content_field,
                     dataset_title_field, dataset_row_filter)
                VALUES (1001, 2001, 2002, 1, 'FILE',
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        9001, TIMESTAMPTZ '2026-07-30T00:00:00Z', 'historical/source.txt',
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                        16, 'text/plain; charset=utf-8', NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                """);
        statement.executeUpdate("""
                INSERT INTO knowledge_data.sync_artifact
                    (id, tenant_id, knowledge_space_id, data_source_id, source_revision,
                     content_digest, record_count, synchronized_at)
                VALUES (3001, 1001, 2001, 2002, 1,
                        'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                        1, TIMESTAMPTZ '2026-07-30T00:00:00Z')
                """);
        statement.executeUpdate("""
                INSERT INTO knowledge_data.published_sync_artifact
                    (tenant_id, knowledge_space_id, data_source_id, source_version, artifact_id,
                     quality_status, rule_set_version, published_at)
                VALUES (1001, 2001, 2002, 1, 3001, 'PASS', 'file-text-v1',
                        TIMESTAMPTZ '2026-07-30T00:00:00Z')
                """);
    }
}
