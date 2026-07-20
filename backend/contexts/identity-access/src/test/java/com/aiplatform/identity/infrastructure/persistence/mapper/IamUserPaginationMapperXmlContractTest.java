package com.aiplatform.identity.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IamUserPaginationMapperXmlContractTest {
    @Test
    void countAndPageQueriesShareTenantScopeAndUseStableDatabasePagination() throws IOException {
        String xml;
        try (var input = getClass().getClassLoader()
                .getResourceAsStream("mapper/identity/IamAuthorizationMapper.xml")) {
            assertThat(input).as("IAM authorization mapper XML").isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String scope = fragment(xml, "ManageableIamUserPredicate", "</sql>");
        String count = statement(xml, "countIamUsers");
        String page = statement(xml, "findIamUsers");

        assertThat(scope)
                .contains("u.tenant_id = #{tenantId}")
                .contains("u.deleted_at IS NULL")
                .contains("system_ur.tenant_id = u.tenant_id")
                .contains("system_role.role_type = 'system'");
        assertThat(count).contains("<include refid=\"ManageableIamUserPredicate\"/>");
        assertThat(page)
                .contains("<include refid=\"ManageableIamUserPredicate\"/>")
                .contains("ORDER BY u.display_name ASC, u.id ASC")
                .contains("LIMIT #{limit}")
                .contains("OFFSET #{offset}");
    }

    private static String statement(String xml, String id) {
        return fragment(xml, id, "</select>");
    }

    private static String fragment(String xml, String id, String closingTag) {
        int start = xml.indexOf("id=\"" + id + "\"");
        assertThat(start).as("mapper statement " + id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf(closingTag, start);
        assertThat(end).as("closing tag for " + id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
