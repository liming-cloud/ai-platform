package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApplicationReleaseOutboxMapperXmlTest {
    private static final String RESOURCE = "mapper/appstudio/ApplicationReleaseOutboxMapper.xml";

    @Test
    void enqueueBindsStringTenantIdToTheNumericOutboxColumn() throws Exception {
        String sql = normalized(statement("enqueue").getBoundSql(Map.of("message", message())).getSql());

        assertThat(sql)
                .contains("insert into app_application_release_outbox")
                .contains("cast(? as bigint)")
                .contains("cast(? as jsonb)")
                .contains("on conflict (event_id) do nothing");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ApplicationReleaseOutboxMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static DomainEventMessage message() {
        return new DomainEventMessage(
                "ApplicationPublished:2001:9001:1",
                "ApplicationPublished",
                "2001",
                "AiApplication",
                "9001",
                1,
                "v1",
                Instant.parse("2026-07-22T00:00:00Z"),
                "trace-app-release-9001",
                "app-studio",
                "{\"applicationId\":\"9001\"}"
        );
    }
}
