package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppApplicationReleaseMapperXmlTest {
    @Test
    void mutationLocksTenantApplicationBeforeAllocatingVersionAndSwitchesScopedPointer() throws Exception {
        String xml = mapperXml();

        assertThat(statement(xml, "id=\"lockApplication\""))
                .contains("flushCache=\"true\"")
                .contains("useCache=\"false\"")
                .contains("tenant_id = #{tenantId}")
                .contains("id = #{applicationId}")
                .contains("deleted_at IS NULL")
                .contains("FOR UPDATE");
        assertThat(statement(xml, "id=\"activateRelease\""))
                .contains("tenant_id, application_id, active_release_id")
                .contains("ON CONFLICT (tenant_id, application_id) DO UPDATE");
        assertThat(statement(xml, "id=\"markApplicationPublished\""))
                .contains("tenant_id = #{tenantId}")
                .contains("id = #{applicationId}")
                .contains("version = version + 1");
    }

    @Test
    void everyReleaseReadIsTenantAndApplicationScoped() throws Exception {
        String xml = mapperXml();

        for (String id : new String[] {"findActive", "findByRequestId", "findById", "findAll"}) {
            assertThat(statement(xml, "id=\"" + id + "\""))
                    .as(id)
                    .contains("release.tenant_id = #{tenantId}")
                    .contains("release.application_id = #{applicationId}");
        }
        assertThat(statement(xml, "id=\"findActiveByApplicationIds\""))
                .contains("release.tenant_id = #{tenantId}")
                .contains("collection=\"applicationIds\"")
                .contains("release.application_id IN");
    }

    private static String statement(String xml, String id) {
        int start = xml.indexOf(id);
        assertThat(start).as("mapper statement %s must exist", id).isGreaterThanOrEqualTo(0);
        int next = xml.indexOf(" id=\"", start + id.length());
        return next < 0 ? xml.substring(start) : xml.substring(start, next);
    }

    private static String mapperXml() throws Exception {
        try (InputStream stream = AppApplicationReleaseMapperXmlTest.class.getResourceAsStream(
                "/mapper/appstudio/AppApplicationReleaseMapper.xml")) {
            assertThat(stream).as("AppApplicationReleaseMapper.xml must exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }
}
