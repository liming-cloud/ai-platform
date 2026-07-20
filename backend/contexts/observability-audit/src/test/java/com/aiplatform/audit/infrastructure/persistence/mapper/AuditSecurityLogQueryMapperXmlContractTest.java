package com.aiplatform.audit.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AuditSecurityLogQueryMapperXmlContractTest {
    @Test
    void countAndPageQueriesShareTenantScopedFiltersAndStableDatabasePagination() throws IOException {
        String resource = "/mapper/audit/AuditSecurityLogQueryMapper.xml";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(xml).contains(
                    "<sql id=\"AuditSecurityLogWhere\">",
                    "WHERE tenant_id = #{query.tenantId}",
                    "actor_id = #{query.actorId}",
                    "event_type = #{query.eventType}",
                    "subject_type = #{query.subjectType}",
                    "subject_ref = #{query.subjectRef}",
                    "trace_id = #{query.traceId}",
                    "risk_level = #{query.riskLevel}",
                    "result_status = #{query.resultStatus}",
                    "created_at &gt;= #{query.createdFrom}",
                    "created_at &lt;= #{query.createdTo}",
                    "<select id=\"countLogs\"",
                    "<select id=\"findLogs\"",
                    "ORDER BY created_at DESC, id DESC",
                    "LIMIT #{query.pageSize}",
                    "OFFSET #{query.offset}"
            );
            assertThat(xml.split("<include refid=\"AuditSecurityLogWhere\"/>", -1)).hasSize(3);
        }
    }

    @Test
    void exposesTheComputedOffsetThroughMyBatisPropertyResolution() {
        AuditSecurityLogQuery query = new AuditSecurityLogQuery(
                2001L, null, null, null, null, null, null, null, null, null, 3, 50
        );

        assertThat(SystemMetaObject.forObject(query).getValue("offset")).isEqualTo(100L);
    }

    @Test
    void parsesAsARealMyBatisMapper() throws IOException {
        String resource = "mapper/audit/AuditSecurityLogQueryMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream stream = getClass().getResourceAsStream("/" + resource)) {
            assertThat(stream).as(resource).isNotNull();
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(AuditSecurityLogQueryMapper.class.getName() + ".countLogs")).isTrue();
        assertThat(configuration.hasStatement(AuditSecurityLogQueryMapper.class.getName() + ".findLogs")).isTrue();
    }

    @Test
    void mapsOpaqueSubjectReferenceAlongsideLegacyNumericProjection() throws IOException {
        String resource = "/mapper/audit/AuditSecurityLogQueryMapper.xml";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(xml).contains(
                    "<result property=\"subjectId\" column=\"subject_id\"/>",
                    "<result property=\"subjectRef\" column=\"subject_ref\"/>",
                    "subject_id,",
                    "subject_ref,"
            );
        }
    }
}
