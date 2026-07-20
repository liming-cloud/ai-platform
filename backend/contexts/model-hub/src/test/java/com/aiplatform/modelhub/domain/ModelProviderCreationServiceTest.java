package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelProviderCreationServiceTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-05-22T03:00:00Z");

    @Test
    void normalizesCreationFactsBeforeApprovalChangeUse() {
        ModelProviderCreation creation = new ModelProviderCreationService().createProvider(
                TenantId.of("2001"),
                " 9001 ",
                " OpenAI ",
                " OpenAI 企业账号 ",
                " https://api.openai.example/v1 ",
                " API_KEY ",
                " 1001 ",
                " TRACE-MODEL-PROVIDER-001 ",
                REQUESTED_AT
        );

        assertThat(creation.provider().providerId()).isEqualTo("9001");
        assertThat(creation.provider().providerCode()).isEqualTo("openai");
        assertThat(creation.provider().providerName()).isEqualTo("OpenAI 企业账号");
        assertThat(creation.provider().endpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(creation.provider().authType()).isEqualTo("api_key");
        assertThat(creation.configChange().providerId()).isEqualTo("9001");
        assertThat(creation.configChange().changeSummary()).isEqualTo("创建模型供应商 OpenAI 企业账号");
        assertThat(creation.configChange().operatorId()).isEqualTo("1001");
        assertThat(creation.configChange().traceId()).isEqualTo("TRACE-MODEL-PROVIDER-001");
    }

    @Test
    void rejectsBlankCreationFactsBeforeCreatingProvider() {
        assertThatThrownBy(() -> new ModelProviderCreationService().createProvider(
                TenantId.of("2001"),
                " ",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                "1001",
                "TRACE-MODEL-PROVIDER-001",
                REQUESTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商 ID不能为空");

        assertThatThrownBy(() -> new ModelProviderCreationService().createProvider(
                TenantId.of("2001"),
                "9001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                " ",
                "TRACE-MODEL-PROVIDER-001",
                REQUESTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作人 ID不能为空");
    }
}
