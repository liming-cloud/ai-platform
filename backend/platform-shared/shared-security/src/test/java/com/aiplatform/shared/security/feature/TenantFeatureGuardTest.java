package com.aiplatform.shared.security.feature;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class TenantFeatureGuardTest {
    @Test
    void allowsEnabledFeature() {
        TenantFeatureGuard guard = new TenantFeatureGuard((tenantId, featureCode) -> true);

        guard.requireEnabled(TenantId.of("2001"), TenantFeatureCodes.APP_STUDIO);
    }

    @Test
    void rejectsDisabledFeatureWithPermissionDeniedCode() {
        TenantFeatureGuard guard = new TenantFeatureGuard((tenantId, featureCode) -> false);

        assertThatThrownBy(() -> guard.requireEnabled(TenantId.of("2001"), TenantFeatureCodes.KNOWLEDGE_BASE))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-FEATURE-PERMISSIONDENIED");
    }
}
