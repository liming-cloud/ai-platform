package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantFeatureUpdateRequestTest {
    @Test
    void 完整布尔配置可解析且未知缺失和非布尔值均拒绝() {
        var features = new HashMap<String, Object>();
        for (var feature : TenantFeature.values()) features.put(feature.code(), true);
        var request = TenantFeatureUpdateRequest.from(Map.of("availableFeatures", features, "expectedVersion", 0));
        assertEquals(true, request.availableFeatures().get("knowledgeManagement"));

        var unknown = new HashMap<>(features);
        unknown.put("tenantIsolation", false);
        assertThrows(IllegalArgumentException.class, () -> TenantFeatureUpdateRequest.from(
                Map.of("availableFeatures", unknown, "expectedVersion", 0)));
        var missing = new HashMap<>(features);
        missing.remove("conversation");
        assertThrows(IllegalArgumentException.class, () -> TenantFeatureUpdateRequest.from(
                Map.of("availableFeatures", missing, "expectedVersion", 0)));
        var malformed = new HashMap<>(features);
        malformed.put("conversation", "false");
        assertThrows(IllegalArgumentException.class, () -> TenantFeatureUpdateRequest.from(
                Map.of("availableFeatures", malformed, "expectedVersion", 0)));
    }
}
