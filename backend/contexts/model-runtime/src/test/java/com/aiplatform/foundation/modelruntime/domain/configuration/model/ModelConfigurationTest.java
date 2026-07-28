package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelConfigurationTest {

    @Test
    void 平台配置不能绑定租户或成员() {
        assertThrows(IllegalArgumentException.class, () -> ModelConfiguration.create(
                new ModelConfigurationId(1001L),
                ModelConfigurationScope.PLATFORM,
                new TenantId(2001L),
                null,
                "platform-chat",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z")));
    }

    @Test
    void 租户配置必须绑定租户且不得绑定个人成员() {
        var configuration = ModelConfiguration.create(
                new ModelConfigurationId(1001L),
                ModelConfigurationScope.TENANT,
                new TenantId(2001L),
                null,
                "tenant-chat",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertEquals(ModelConfigurationStatus.DRAFT, configuration.status());
        assertEquals(new TenantId(2001L), configuration.tenantId());
        assertEquals(null, configuration.ownerMembershipId());
    }

    @Test
    void 个人配置必须绑定租户和创建成员() {
        assertThrows(IllegalArgumentException.class, () -> ModelConfiguration.create(
                new ModelConfigurationId(1001L),
                ModelConfigurationScope.PERSONAL,
                new TenantId(2001L),
                null,
                "personal-chat",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z")));

        var configuration = ModelConfiguration.create(
                new ModelConfigurationId(1002L),
                ModelConfigurationScope.PERSONAL,
                new TenantId(2001L),
                new ModelConfigurationOwnerId(4001L),
                "personal-chat",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertEquals(new ModelConfigurationOwnerId(4001L), configuration.ownerMembershipId());
    }
}
