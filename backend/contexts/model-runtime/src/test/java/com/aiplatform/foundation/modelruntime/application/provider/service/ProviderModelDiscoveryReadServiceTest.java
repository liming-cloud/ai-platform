package com.aiplatform.foundation.modelruntime.application.provider.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.provider.query.ListProviderModelDiscoveriesQuery;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryHistoryReader;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryHistoryItem;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryHistoryPage;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderModelDiscoveryReadServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION =
            new ProviderConnectionId(310000000000000001L);

    @Test
    void 只向有模型读取权限的当前租户返回服务端分页安全摘要() {
        var reader = new RecordingReader();
        var service = new ProviderModelDiscoveryReadService(
                reader,
                Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC));

        var page = service.list(new ListProviderModelDiscoveriesQuery(
                context("model:read"), CONNECTION.toString(), new PageQuery(2, 20)));

        assertEquals(2, page.pageNumber());
        assertEquals(20, page.pageSize());
        assertEquals(TENANT, reader.tenantId);
        assertEquals(CONNECTION, reader.connectionId);
        assertEquals(2, reader.pageNumber);
        assertEquals(20, reader.pageSize);
        assertEquals("CREATE_DEPLOYMENT", page.items().getFirst().nextStep());
    }

    @Test
    void 无读取权限时不得查询任何发现历史() {
        var reader = new RecordingReader();
        var service = new ProviderModelDiscoveryReadService(reader, Clock.systemUTC());

        assertThrows(
                com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException.class,
                () -> service.list(new ListProviderModelDiscoveriesQuery(
                        context("model:write"), CONNECTION.toString(), new PageQuery(1, 20))));
        assertEquals(null, reader.tenantId);
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trc_model_discovery_read");
    }

    private static final class RecordingReader implements ProviderModelDiscoveryHistoryReader {
        private TenantId tenantId;
        private ProviderConnectionId connectionId;
        private long pageNumber;
        private long pageSize;

        @Override
        public ProviderModelDiscoveryHistoryPage findPage(
                TenantId tenantId,
                ProviderConnectionId connectionId,
                long pageNumber,
                long pageSize) {
            this.tenantId = tenantId;
            this.connectionId = connectionId;
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
            return new ProviderModelDiscoveryHistoryPage(
                    List.of(new ProviderModelDiscoveryHistoryItem(
                            "340000000000000001", 1, true, 2,
                            "DETERMINISTIC_DISCOVERY_OK", 10,
                            Instant.parse("2026-08-10T02:00:00Z"),
                            Instant.parse("2026-08-11T02:00:00Z"))),
                    21);
        }
    }
}
