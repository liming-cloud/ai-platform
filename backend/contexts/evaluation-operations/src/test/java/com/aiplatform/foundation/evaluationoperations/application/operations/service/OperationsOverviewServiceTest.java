package com.aiplatform.foundation.evaluationoperations.application.operations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.assistantdelivery.application.operations.dto.AssistantOperationsFacts;
import com.aiplatform.foundation.assistantdelivery.application.operations.facade.AssistantOperationsFactSource;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.EvaluationOperationsFacts;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview.OperationsRange;
import com.aiplatform.foundation.evaluationoperations.application.operations.port.out.EvaluationOperationsFactSource;
import com.aiplatform.foundation.evaluationoperations.domain.operations.model.OperationsQuotaPolicy;
import com.aiplatform.foundation.knowledgedata.application.operations.dto.KnowledgeOperationsFacts;
import com.aiplatform.foundation.knowledgedata.application.operations.facade.KnowledgeOperationsFactSource;
import com.aiplatform.foundation.modelruntime.application.operations.dto.ModelOperationsFacts;
import com.aiplatform.foundation.modelruntime.application.operations.facade.ModelOperationsFactSource;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.PlatformOrganizationOperationsFacts;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.TenantOperationsIdentity;
import com.aiplatform.foundation.organizationaccess.application.operations.dto.TenantOrganizationOperationsFacts;
import com.aiplatform.foundation.organizationaccess.application.operations.facade.OrganizationOperationsFactSource;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.operations.dto.QueryOperationsSummaryFacts;
import com.aiplatform.foundation.trustedquery.application.operations.dto.QueryDailyOperationsFacts;
import com.aiplatform.foundation.trustedquery.application.operations.dto.QueryUsageFacts;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOverviewFactSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationsOverviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:34:56Z");
    private static final TenantId TENANT_ID = new TenantId(7L);

    private OrganizationOperationsFactSource organization;
    private KnowledgeOperationsFactSource knowledge;
    private ModelOperationsFactSource models;
    private AssistantOperationsFactSource assistants;
    private EvaluationOperationsFactSource evaluations;
    private QueryOverviewFactSource queries;
    private QueryOperationsFactSource usage;
    private OperationsOverviewService service;

    @BeforeEach
    void setUp() {
        organization = mock(OrganizationOperationsFactSource.class);
        knowledge = mock(KnowledgeOperationsFactSource.class);
        models = mock(ModelOperationsFactSource.class);
        assistants = mock(AssistantOperationsFactSource.class);
        evaluations = mock(EvaluationOperationsFactSource.class);
        queries = mock(QueryOverviewFactSource.class);
        usage = mock(QueryOperationsFactSource.class);
        service = new OperationsOverviewService(
                organization,
                knowledge,
                models,
                assistants,
                evaluations,
                queries,
                usage,
                new OperationsQuotaPolicy(100, 10),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 平台总览必须组合脱敏事实并按租户去重注意项() {
        when(organization.platformSummary()).thenReturn(new PlatformOrganizationOperationsFacts(
                Map.of("ACTIVE", 2L), Map.of("ACTIVE", 1L), Map.of("ENABLED", 2L), Map.of("ENABLED", 8L)));
        when(knowledge.platformSummary()).thenReturn(new KnowledgeOperationsFacts(
                Map.of("ACTIVE", 3L), Map.of("READY", 4L), Map.of("ACTIVE", 2L), Map.of("FAILED", 1L)));
        when(models.platformSummary()).thenReturn(new ModelOperationsFacts(
                Map.of("VERIFIED", 2L), Map.of("PUBLISHED", 3L), Map.of(), Map.of(), Map.of()));
        when(queries.platformSummary(
                Instant.parse("2026-08-10T00:00:00Z"), NOW))
                .thenReturn(new QueryOperationsSummaryFacts(
                        10L, 7L, 1L, 1L, 1L, 120L,
                        java.util.List.of(new QueryDailyOperationsFacts(
                                LocalDate.parse("2026-08-16"), 3L, 2L, 0L, 1L, 42L))));
        when(queries.attentionTenants(Instant.parse("2026-08-10T00:00:00Z"), NOW))
                .thenReturn(Set.of(new TenantId(1L), new TenantId(2L)));
        when(models.tenantsWithoutPublishedDeployment())
                .thenReturn(Set.of(new TenantId(2L), new TenantId(3L)));

        var overview = service.platformOverview(platform(), OperationsRange.DAY_7);

        assertEquals(3L, overview.attentionTenantCount());
        assertEquals(0.7d, overview.queries().rates().answerRate());
        assertEquals(LocalDate.parse("2026-08-16"), overview.queries().daily().getFirst().date());
        assertEquals(42L, overview.queries().daily().getFirst().totalTokens());
        assertEquals(NOW.plusSeconds(300L), overview.staleAfter());
    }

    @Test
    void 租户总览必须只向事实源传递当前租户并包含配额() {
        when(organization.tenantSummary(TENANT_ID)).thenReturn(new TenantOrganizationOperationsFacts(
                Map.of("ENABLED", 1L), Map.of("ENABLED", 4L, "DISABLED", 1L)));
        when(knowledge.tenantSummary(TENANT_ID)).thenReturn(new KnowledgeOperationsFacts(
                Map.of("ACTIVE", 1L), Map.of("READY", 2L), Map.of("ACTIVE", 1L), Map.of()));
        when(models.tenantSummary(TENANT_ID)).thenReturn(new ModelOperationsFacts(
                Map.of(), Map.of(), Map.of("VERIFIED", 1L), Map.of("PUBLISHED", 1L), Map.of("ACTIVE", 2L)));
        when(assistants.tenantSummary(TENANT_ID)).thenReturn(new AssistantOperationsFacts(
                2L, Map.of("PUBLISHED", 1L), Map.of("ANSWERED", 3L)));
        when(evaluations.tenantSummary(TENANT_ID)).thenReturn(new EvaluationOperationsFacts(
                Map.of("PASSED", 1L), Map.of("AUTHORIZED", 1L)));
        when(queries.tenantSummary(
                TENANT_ID, Instant.parse("2026-08-10T00:00:00Z"), NOW))
                .thenReturn(new QueryOperationsSummaryFacts(4L, 3L, 1L, 0L, 0L, 80L));
        when(usage.summarize(org.mockito.ArgumentMatchers.any())).thenReturn(new QueryUsageFacts(
                4L, 3L, 1L, 0L, 0L, 40L, 40L, 80L, 25L, 2L));

        var overview = service.tenantOverview(tenant(), OperationsRange.DAY_7);

        assertEquals(25L, overview.quota().dailyUsed());
        assertEquals(100, overview.quota().dailyLimit());
        assertEquals(2L, overview.quota().concurrentUsed());
        assertEquals(5L, overview.members().users().total());
    }

    @Test
    void 平台租户摘要必须保留服务器分页顺序并以零值补齐无查询租户() {
        var page = new PageQuery(2, 2);
        when(organization.listTenants(page)).thenReturn(PageResult.of(List.of(
                new TenantOperationsIdentity(7L, "门店租户", "ACTIVE"),
                new TenantOperationsIdentity(8L, "校园租户", "SUSPENDED")), page, 5));
        when(queries.tenantSummaries(
                List.of(new TenantId(7L), new TenantId(8L)),
                Instant.parse("2026-08-10T00:00:00Z"), NOW))
                .thenReturn(Map.of(new TenantId(7L),
                        new QueryOperationsSummaryFacts(3L, 2L, 0L, 1L, 0L, 12L)));
        when(queries.attentionTenants(Instant.parse("2026-08-10T00:00:00Z"), NOW))
                .thenReturn(Set.of(new TenantId(7L)));
        when(models.tenantsWithoutPublishedDeployment())
                .thenReturn(Set.of(new TenantId(8L)));

        var summaries = service.tenantSummaries(platform(), OperationsRange.DAY_7, page);

        assertEquals(5L, summaries.totalElements());
        assertEquals(List.of("门店租户", "校园租户"),
                summaries.items().stream().map(item -> item.tenantName()).toList());
        assertEquals(3L, summaries.items().getFirst().queries().accepted());
        assertEquals(0L, summaries.items().getLast().queries().accepted());
        assertEquals(true, summaries.items().getFirst().attentionRequired());
        assertEquals(true, summaries.items().getLast().attentionRequired());
    }

    private static PlatformRequestContext platform() {
        return new PlatformRequestContext(
                new SubjectId(1L), CredentialType.PERSON, AccessChannel.CONSOLE, 0L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.OPERATIONS_READ), "trace-platform");
    }

    private static TrustedRequestContext tenant() {
        return new TrustedRequestContext(
                TENANT_ID, new SubjectId(9L), new MembershipId(10L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 0L, Set.of(new PermissionCode("access:manage")), "trace-tenant");
    }
}
