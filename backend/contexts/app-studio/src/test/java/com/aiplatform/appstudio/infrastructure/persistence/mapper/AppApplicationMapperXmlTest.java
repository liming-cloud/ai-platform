package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppApplicationMapperXmlTest {
    @Test
    void mutationLockAndCatalogBindingBatchStayTenantAndApplicationScoped() throws Exception {
        String xml = mapperXml();

        assertThat(statement(xml, "id=\"lockApplication\""))
                .contains("flushCache=\"true\"")
                .contains("useCache=\"false\"")
                .contains("tenant_id = #{tenantId}")
                .contains("id = #{applicationId}")
                .contains("deleted_at IS NULL")
                .contains("FOR UPDATE");
        assertThat(statement(xml, "id=\"findActiveBindingsByApplicationIds\""))
                .contains("tenant_id = #{tenantId}")
                .contains("application_id IN")
                .contains("collection=\"applicationIds\"")
                .contains("deleted_at IS NULL")
                .contains("status = 'ACTIVE'");
    }

    private static String statement(String xml, String id) {
        int start = xml.indexOf(id);
        assertThat(start).as("mapper statement %s must exist", id).isGreaterThanOrEqualTo(0);
        int next = xml.indexOf(" id=\"", start + id.length());
        return next < 0 ? xml.substring(start) : xml.substring(start, next);
    }

    private static String mapperXml() throws Exception {
        try (InputStream stream = AppApplicationMapperXmlTest.class.getResourceAsStream(
                "/mapper/appstudio/AppApplicationMapper.xml")) {
            assertThat(stream).as("AppApplicationMapper.xml must exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }
}
