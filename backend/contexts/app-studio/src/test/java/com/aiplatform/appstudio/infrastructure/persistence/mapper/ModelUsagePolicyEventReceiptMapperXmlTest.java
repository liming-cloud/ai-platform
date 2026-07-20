package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyEventReceiptMapperXmlTest {
    private static final String RESOURCE = "mapper/appstudio/ModelUsagePolicyEventReceiptMapper.xml";

    @Test
    void insertUsesTheEventIdAsThePersistentIdempotencyKey() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        MappedStatement statement = configuration.getMappedStatement(
                ModelUsagePolicyEventReceiptMapper.class.getName() + ".insertIfAbsent"
        );
        String sql = statement.getBoundSql(Map.of(
                "eventId", "event-1",
                "eventVersion", 1L,
                "occurredAt", Instant.parse("2026-07-17T08:00:00Z")
        )).getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
        assertThat(sql)
                .contains("insert into app_model_usage_policy_event_receipt")
                .contains("event_id")
                .contains("event_version")
                .contains("on conflict (event_id) do nothing");
    }
}
