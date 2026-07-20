package com.aiplatform.identity.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IamPermissionRequestMapperXmlContractTest {
    @Test
    void pageQueryCountsAndFiltersInDatabaseWithDeterministicOrder() throws Exception {
        String xml = new ClassPathResource("mapper/identity/IamPermissionRequestMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml)
                .contains("SELECT COUNT(*) FROM iam_permission_request")
                .contains("AND status = #{status}")
                .contains("created_at DESC")
                .contains("request_id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}");
    }
}
