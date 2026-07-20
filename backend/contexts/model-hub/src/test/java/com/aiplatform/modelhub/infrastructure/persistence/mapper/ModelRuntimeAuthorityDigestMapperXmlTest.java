package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ModelRuntimeAuthorityDigestMapperXmlTest {
    private static final String RESOURCE = "mapper/modelhub/ModelRuntimeAuthorityDigestMapper.xml";

    @Test
    void effectiveOptionsDigestUsesTheDatabaseOwnedCanonicalFunction() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        MappedStatement statement = configuration.getMappedStatement(
                ModelRuntimeAuthorityDigestMapper.class.getName() + ".canonicalEffectiveOptionsDigest"
        );
        String sql = statement.getBoundSql(Map.of("effectiveOptionsJson", "{\"temperature\":0.2}"))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(sql).contains("mdl_model_runtime_options_digest(cast(? as jsonb))");
    }
}
