package com.aiplatform.appstudio.infrastructure.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.domain.publish.PublishPreflightIssue;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppPublishPreflightPersistenceConverterTest {
    @Test
    void roundTripsJsonbIssuesWithoutLosingStableCodes() {
        AppPublishPreflightPersistenceConverter converter = new AppPublishPreflightPersistenceConverter(new JsonMapper());
        AppPublishPreflightResult result = new AppPublishPreflightResult(
                TenantId.of("2001"),
                "3001",
                "publish-1",
                "a".repeat(64),
                false,
                List.of(new PublishPreflightIssue(
                        "knowledge_base_not_visible",
                        "绑定知识库不存在或当前主体不可见",
                        "KNOWLEDGE_BASE",
                        "20"
                )),
                List.of(),
                "trace-1",
                "1001",
                Instant.parse("2026-07-10T03:00:00Z")
        );

        var restored = converter.toDomain(converter.fromDomain(result));

        assertThat(restored).isEqualTo(result);
        assertThat(converter.fromDomain(result).getCandidateFingerprint()).isEqualTo("a".repeat(64));
        assertThat(converter.fromDomain(result).getBlockersJson()).contains("knowledge_base_not_visible");
    }
}
