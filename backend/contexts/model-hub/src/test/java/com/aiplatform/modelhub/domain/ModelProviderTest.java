package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ModelProviderTest {
    @Test
    void rejectsBlankRequiredProviderFactsInsteadOfInvalidProvider() {
        assertBlankRejected("供应商 ID", " ", "openai", "OpenAI", "https://api.openai.com/v1");
        assertBlankRejected("供应商编码", "3001", " ", "OpenAI", "https://api.openai.com/v1");
        assertBlankRejected("供应商名称", "3001", "openai", " ", "https://api.openai.com/v1");
        assertBlankRejected("供应商访问地址", "3001", "openai", "OpenAI", " ");
    }

    @Test
    void allowsPendingCredentialProviderWithoutSecretReference() {
        ModelProvider provider = credentialProvider(ModelProviderStatus.PENDING_APPROVAL, null);

        assertThat(provider.secretRef()).isNull();
        assertThat(provider.status()).isEqualTo(ModelProviderStatus.PENDING_APPROVAL);
    }

    @Test
    void rejectsActiveCredentialProviderWithoutSecretReference() {
        assertThatThrownBy(() -> credentialProvider(ModelProviderStatus.ACTIVE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ACTIVE 模型供应商凭证材料引用不能为空");
    }

    @Test
    void approvingCredentialProviderWithoutSecretReferenceFailsClosed() {
        ModelProvider provider = credentialProvider(ModelProviderStatus.PENDING_APPROVAL, null);

        assertThatThrownBy(() -> provider.approve(Instant.parse("2026-05-22T02:00:00Z")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ACTIVE 模型供应商凭证材料引用不能为空");
    }

    @Test
    void approvalDecisionCannotResurrectDisabledProvider() {
        ModelProvider provider = credentialProvider(
                ModelProviderStatus.DISABLED,
                new ModelSecretRef("9001", "sk-***cret")
        );

        assertThatThrownBy(() -> provider.approve(Instant.parse("2026-05-22T02:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("待审批");
    }

    @Test
    void doesNotExposeUnpersistedCapabilitiesAsProviderFact() {
        assertThat(Arrays.stream(ModelProvider.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("capabilities");
        assertThat(Arrays.stream(ModelProvider.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("canServe");
    }

    private static ModelProvider credentialProvider(ModelProviderStatus status, ModelSecretRef secretRef) {
        return credentialProvider("3001", "openai", "OpenAI", "https://api.openai.com/v1", status, secretRef);
    }

    private static void assertBlankRejected(
            String fieldName,
            String providerId,
            String providerCode,
            String providerName,
            String endpoint
    ) {
        assertThatThrownBy(() -> credentialProvider(
                providerId,
                providerCode,
                providerName,
                endpoint,
                ModelProviderStatus.PENDING_APPROVAL,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fieldName + "不能为空");
    }

    private static ModelProvider credentialProvider(
            String providerId,
            String providerCode,
            String providerName,
            String endpoint,
            ModelProviderStatus status,
            ModelSecretRef secretRef
    ) {
        return new ModelProvider(
                TenantId.of("2001"),
                providerId,
                providerCode,
                providerName,
                endpoint,
                "api_key",
                secretRef,
                status,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T00:00:00Z"),
                Instant.parse("2026-05-22T01:00:00Z")
        );
    }
}
