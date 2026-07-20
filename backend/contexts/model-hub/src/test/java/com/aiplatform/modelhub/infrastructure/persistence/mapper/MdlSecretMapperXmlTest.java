package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MdlSecretMapperXmlTest {
    private static final String RESOURCE = "mapper/modelhub/MdlSecretMapper.xml";

    @Test
    void rotationCasIsTenantVersionAndOldMetadataFenced() throws Exception {
        String sql = normalized(statement("rotateIfUnchanged").getBoundSql(Map.ofEntries(
                Map.entry("id", 9001L),
                Map.entry("tenantId", 2001L),
                Map.entry("expectedVersion", 7),
                Map.entry("oldKeyId", "kms-old"),
                Map.entry("oldAlgorithm", "AES-256-GCM"),
                Map.entry("oldCipherVersion", 1),
                Map.entry("newCiphertext", "ciphertext"),
                Map.entry("newKeyId", "kms-active"),
                Map.entry("newAlgorithm", "AES-256-GCM"),
                Map.entry("newCipherVersion", 2),
                Map.entry("rotatedAt", Instant.parse("2026-07-18T02:00:00Z"))
        )).getSql());

        assertThat(sql)
                .contains("where tenant_id = ?")
                .contains("and id = ?")
                .contains("and version = ?")
                .contains("and kms_key_id = ?")
                .contains("and cipher_algorithm = ?")
                .contains("and cipher_version = ?")
                .contains("and status = 'active'")
                .contains("and deleted_at is null")
                .contains("version = version + 1");
    }

    @Test
    void candidateAndBacklogUseTheSameNullSafeTargetPredicate() throws Exception {
        Map<String, Object> parameters = Map.of(
                "activeKeyId", "kms-active",
                "targetAlgorithm", "AES-256-GCM",
                "targetVersion", 2,
                "afterId", 9001L,
                "limit", 20
        );
        String candidateSql = normalized(statement("findRotationCandidates").getBoundSql(parameters).getSql());
        String backlogSql = normalized(statement("countRotationBacklog").getBoundSql(parameters).getSql());

        assertThat(candidateSql).contains(
                "kms_key_id is distinct from ?",
                "cipher_algorithm is distinct from ?",
                "cipher_version is distinct from ?",
                "order by case when id > ? then 0 else 1 end, id",
                "limit ?"
        );
        assertThat(backlogSql).contains(
                "kms_key_id is distinct from ?",
                "cipher_algorithm is distinct from ?",
                "cipher_version is distinct from ?"
        );
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(MdlSecretMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
