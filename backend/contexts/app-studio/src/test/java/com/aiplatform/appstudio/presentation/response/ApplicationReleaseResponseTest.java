package com.aiplatform.appstudio.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.appstudio.application.result.AiApplicationCatalogResult;
import com.aiplatform.appstudio.application.result.AiApplicationDetailResult;
import com.aiplatform.appstudio.application.result.ApplicationAccessMode;
import com.aiplatform.appstudio.application.result.ApplicationCapabilitiesResult;
import com.aiplatform.appstudio.application.result.ApplicationReleaseDetailResult;
import com.aiplatform.appstudio.application.result.ApplicationReleaseSummaryResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationReleaseResponseTest {
    private static final Instant APPLICATION_CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void catalogResponseFlattensTheServerTrustedApplicationAccessContract() {
        AiApplicationCatalogResponse response = AiApplicationCatalogResponse.from(new AiApplicationCatalogResult(
                new AiApplicationDetailResult(
                        "3001",
                        "APP-3001",
                        "制度助手",
                        null,
                        "KNOWLEDGE_QA",
                        null,
                        List.of(),
                        null,
                        "PUBLISHED",
                        APPLICATION_CREATED_AT,
                        null,
                        "7001",
                        1L,
                        "PUBLISH",
                        null,
                        null
                ),
                ApplicationAccessMode.GOVERNANCE_READ_ONLY,
                ApplicationCapabilitiesResult.governanceReadOnly()
        ));

        assertThat(response.accessMode()).isEqualTo("GOVERNANCE_READ_ONLY");
        assertThat(response.capabilities().canReadReleaseEvidence()).isTrue();
        assertThat(response.capabilities().canEditConfiguration()).isFalse();
        assertThat(response.modelConfigId()).isNull();
        assertThat(response.knowledgeBaseIds()).isEmpty();
        assertThat(response.systemPrompt()).isNull();
    }

    @Test
    void applicationDetailResponseKeepsActiveAndSourceVersionsInTheirNamedFields() {
        AiApplicationDetailResponse response = AiApplicationDetailResponse.from(new AiApplicationDetailResult(
                "3001",
                "APP-3001",
                "制度助手",
                "企业制度问答",
                "KNOWLEDGE_QA",
                "9001",
                List.of("20"),
                "完整提示词",
                "PUBLISHED",
                APPLICATION_CREATED_AT,
                PUBLISHED_AT,
                "7003",
                3L,
                "ROLLBACK",
                "7001",
                1L
        ));

        assertThat(response.activeReleaseId()).isEqualTo("7003");
        assertThat(response.activeReleaseVersion()).isEqualTo(3L);
        assertThat(response.activeReleaseOperation()).isEqualTo("ROLLBACK");
        assertThat(response.sourceReleaseId()).isEqualTo("7001");
        assertThat(response.sourceReleaseVersion()).isEqualTo(1L);
    }

    @Test
    void summaryResponsePreservesTheVersionCompareAndImpactContract() {
        ApplicationReleaseSummaryResponse response = ApplicationReleaseSummaryResponse.from(
                new ApplicationReleaseSummaryResult(
                        "7003",
                        3,
                        true,
                        "ROLLBACK",
                        "7001",
                        1L,
                        "制度助手",
                        "KNOWLEDGE_QA",
                        "9001",
                        List.of("20", "21"),
                        "提示词摘要",
                        "prompt-sha256",
                        2,
                        8,
                        "1001",
                        "回滚生产版本",
                        "TRACE-ROLLBACK",
                        PUBLISHED_AT,
                        PUBLISHED_AT
                )
        );

        assertThat(response).isEqualTo(new ApplicationReleaseSummaryResponse(
                "7003",
                3,
                true,
                "ROLLBACK",
                "7001",
                1L,
                "制度助手",
                "KNOWLEDGE_QA",
                "9001",
                List.of("20", "21"),
                "提示词摘要",
                "prompt-sha256",
                2,
                8,
                "1001",
                "回滚生产版本",
                "TRACE-ROLLBACK",
                PUBLISHED_AT,
                PUBLISHED_AT
        ));
    }

    @Test
    void detailResponsePreservesTheImmutableSnapshotAndRollbackSource() {
        ApplicationReleaseDetailResponse response = ApplicationReleaseDetailResponse.from(
                new ApplicationReleaseDetailResult(
                        "7003",
                        3,
                        true,
                        "ROLLBACK",
                        "7001",
                        1L,
                        "3001",
                        "制度助手",
                        "企业制度问答",
                        "KNOWLEDGE_QA",
                        "9001",
                        List.of("20"),
                        List.of("tool-1"),
                        "完整提示词",
                        "prompt-sha256",
                        1,
                        5,
                        "1001",
                        "回滚生产版本",
                        "TRACE-ROLLBACK",
                        APPLICATION_CREATED_AT,
                        PUBLISHED_AT
                )
        );

        assertThat(response).isEqualTo(new ApplicationReleaseDetailResponse(
                "7003",
                3,
                true,
                "ROLLBACK",
                "7001",
                1L,
                "3001",
                "制度助手",
                "企业制度问答",
                "KNOWLEDGE_QA",
                "9001",
                List.of("20"),
                List.of("tool-1"),
                "完整提示词",
                "prompt-sha256",
                1,
                5,
                "1001",
                "回滚生产版本",
                "TRACE-ROLLBACK",
                APPLICATION_CREATED_AT,
                PUBLISHED_AT
        ));
    }
}
