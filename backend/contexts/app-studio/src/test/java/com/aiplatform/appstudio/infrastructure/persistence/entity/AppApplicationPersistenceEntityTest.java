package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppApplicationPersistenceEntityTest {
    @Test
    void fromDomainSetsPublishedAtForPublishedApplication() {
        Instant createdAt = Instant.parse("2026-06-11T09:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-11T10:00:00Z");
        AiApplication application = new AiApplication(
                TenantId.of("2001"),
                "3001",
                AiApplicationType.KNOWLEDGE_QA,
                "制度助手",
                "企业制度问答",
                "9001",
                List.of("21"),
                List.of(),
                Set.of("1001"),
                "请引用原文回答",
                ApplicationPublicationStatus.PUBLISHED,
                createdAt,
                updatedAt
        );

        AppApplicationPersistenceEntity entity = AppApplicationPersistenceEntity.fromDomain(application);

        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getPublishedAt()).isEqualTo(updatedAt);
    }
}
