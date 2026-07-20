package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class KbModelUsagePolicyPayloadDigestMapperXmlTest {
    private static final String RESOURCE = "mapper/knowledge/KbModelUsagePolicyPayloadDigestMapper.xml";

    @Test
    void delegatesCanonicalizationToTheDatabaseOwnedPolicyDigestFunction() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        MappedStatement statement = configuration.getMappedStatement(
                KbModelUsagePolicyPayloadDigestMapper.class.getName() + ".canonicalDigest"
        );
        String sql = statement.getBoundSql(Map.of("payloadJson", "{}")).getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(sql).contains("mdl_model_usage_policy_digest(cast(? as jsonb))");
    }
}
