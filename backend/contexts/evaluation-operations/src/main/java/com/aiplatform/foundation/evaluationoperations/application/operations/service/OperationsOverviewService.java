package com.aiplatform.foundation.evaluationoperations.application.operations.service;

import com.aiplatform.foundation.assistantdelivery.application.operations.facade.AssistantOperationsFactSource;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.MemberOperationsView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsNoticeView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsQuotaView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.OperationsRates;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryOperationsView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.ResourceStatusView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview.OperationsRange;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview.PlatformOperationsOverviewView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview.PlatformTenantOperationsSummaryView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview.TenantOperationsOverviewView;
import com.aiplatform.foundation.evaluationoperations.application.operations.policy.OperationsAccessPolicy;
import com.aiplatform.foundation.evaluationoperations.application.operations.port.out.EvaluationOperationsFactSource;
import com.aiplatform.foundation.evaluationoperations.domain.operations.model.OperationsQuotaPolicy;
import com.aiplatform.foundation.evaluationoperations.domain.operations.model.ReportingPeriod;
import com.aiplatform.foundation.knowledgedata.application.operations.facade.KnowledgeOperationsFactSource;
import com.aiplatform.foundation.modelruntime.application.operations.facade.ModelOperationsFactSource;
import com.aiplatform.foundation.organizationaccess.application.operations.facade.OrganizationOperationsFactSource;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsSummaryFacts;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOverviewFactSource;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryUsageFactQuery;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 在运营边界内组合各上下文脱敏事实，并执行平台与租户授权。 */
public final class OperationsOverviewService {

    private static final Duration FRESHNESS = Duration.ofMinutes(5L);

    private final OrganizationOperationsFactSource organization;
    private final KnowledgeOperationsFactSource knowledge;
    private final ModelOperationsFactSource models;
    private final AssistantOperationsFactSource assistants;
    private final EvaluationOperationsFactSource evaluations;
    private final QueryOverviewFactSource queries;
    private final QueryOperationsFactSource usage;
    private final OperationsQuotaPolicy quota;
    private final OperationsAccessPolicy access = new OperationsAccessPolicy();
    private final Clock clock;

    public OperationsOverviewService(
            OrganizationOperationsFactSource organization,
            KnowledgeOperationsFactSource knowledge,
            ModelOperationsFactSource models,
            AssistantOperationsFactSource assistants,
            EvaluationOperationsFactSource evaluations,
            QueryOverviewFactSource queries,
            QueryOperationsFactSource usage,
            OperationsQuotaPolicy quota,
            Clock clock) {
        this.organization = Objects.requireNonNull(organization, "组织运营事实源不能为空");
        this.knowledge = Objects.requireNonNull(knowledge, "知识运营事实源不能为空");
        this.models = Objects.requireNonNull(models, "模型运营事实源不能为空");
        this.assistants = Objects.requireNonNull(assistants, "助手运营事实源不能为空");
        this.evaluations = Objects.requireNonNull(evaluations, "评测运营事实源不能为空");
        this.queries = Objects.requireNonNull(queries, "查询总览事实源不能为空");
        this.usage = Objects.requireNonNull(usage, "查询用量事实源不能为空");
        this.quota = Objects.requireNonNull(quota, "查询配额策略不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public PlatformOperationsOverviewView platformOverview(
            PlatformRequestContext context,
            OperationsRange range) {
        access.requirePlatformOverviewRead(context);
        var generatedAt = clock.instant();
        var period = Objects.requireNonNull(range, "运营范围不能为空").periodAt(generatedAt);
        var organizationFacts = organization.platformSummary();
        var knowledgeFacts = knowledge.platformSummary();
        var modelFacts = models.platformSummary();
        var queryFacts = queries.platformSummary(period.from(), period.to());
        var attention = new HashSet<>(queries.attentionTenants(period.from(), period.to()));
        attention.addAll(models.tenantsWithoutAvailableModel(organization.activeTenantIds()));
        var notices = new ArrayList<OperationsNoticeView>();
        if (!attention.isEmpty()) {
            notices.add(new OperationsNoticeView(
                    "WARNING", "存在需要关注的租户",
                    "部分租户出现查询失败、配额接近上限或没有可用模型，请查看租户摘要。",
                    "/platform/operations"));
        }
        return new PlatformOperationsOverviewView(
                range, period.from(), period.to(), generatedAt, generatedAt.plus(FRESHNESS),
                ResourceStatusView.from(organizationFacts.tenantsByStatus()),
                ResourceStatusView.from(organizationFacts.platformAdministratorsByStatus()),
                ResourceStatusView.from(organizationFacts.tenantAdministratorsByStatus()),
                ResourceStatusView.from(organizationFacts.tenantUsersByStatus()),
                ResourceStatusView.from(knowledgeFacts.spacesByStatus()),
                ResourceStatusView.from(knowledgeFacts.dataSourcesByStatus()),
                ResourceStatusView.from(knowledgeFacts.indexesByStatus()),
                ResourceStatusView.from(knowledgeFacts.indexJobsByStatus()),
                ResourceStatusView.from(modelFacts.platformConnectionsByStatus()),
                ResourceStatusView.from(modelFacts.platformDeploymentsByStatus()),
                toQueryView(queryFacts), attention.size(), notices);
    }

    public TenantOperationsOverviewView tenantOverview(
            TrustedRequestContext context,
            OperationsRange range) {
        access.requireTenantOverviewRead(context);
        var generatedAt = clock.instant();
        var period = Objects.requireNonNull(range, "运营范围不能为空").periodAt(generatedAt);
        var tenantId = context.tenantId();
        var organizationFacts = organization.tenantSummary(tenantId);
        var knowledgeFacts = knowledge.tenantSummary(tenantId);
        var modelFacts = models.tenantSummary(tenantId);
        var assistantFacts = assistants.tenantSummary(tenantId);
        var evaluationFacts = evaluations.tenantSummary(tenantId);
        var queryFacts = queries.tenantSummary(tenantId, period.from(), period.to());
        var currentDay = ReportingPeriod.utcDayContaining(generatedAt);
        var quotaFacts = usage.summarize(new QueryUsageFactQuery(
                tenantId, period.from(), period.to(), currentDay.from(), currentDay.to(), generatedAt));
        var notices = notices(knowledgeFacts.indexJobsByStatus(), modelFacts.tenantDeploymentsByStatus(), queryFacts);
        return new TenantOperationsOverviewView(
                range, period.from(), period.to(), generatedAt, generatedAt.plus(FRESHNESS),
                new MemberOperationsView(
                        ResourceStatusView.from(organizationFacts.administratorsByStatus()),
                        ResourceStatusView.from(organizationFacts.usersByStatus())),
                ResourceStatusView.from(knowledgeFacts.spacesByStatus()),
                ResourceStatusView.from(knowledgeFacts.dataSourcesByStatus()),
                ResourceStatusView.from(knowledgeFacts.indexesByStatus()),
                ResourceStatusView.from(knowledgeFacts.indexJobsByStatus()),
                ResourceStatusView.from(modelFacts.tenantConnectionsByStatus()),
                ResourceStatusView.from(modelFacts.tenantDeploymentsByStatus()),
                ResourceStatusView.from(modelFacts.configurationsByStatus()),
                assistantFacts.assistants(),
                ResourceStatusView.from(assistantFacts.versionsByStatus()),
                ResourceStatusView.from(assistantFacts.testRunsByStatus()),
                ResourceStatusView.from(evaluationFacts.runsByStatus()),
                ResourceStatusView.from(evaluationFacts.releaseDecisionsByStatus()),
                toQueryView(queryFacts),
                new OperationsQuotaView(
                        quotaFacts.dailyUsed(), quota.dailyQueryLimit(),
                        quotaFacts.concurrentUsed(), quota.concurrentQueryLimit()),
                notices);
    }

    public PageResult<PlatformTenantOperationsSummaryView> tenantSummaries(
            PlatformRequestContext context,
            OperationsRange range,
            PageQuery page) {
        access.requirePlatformOverviewRead(context);
        var generatedAt = clock.instant();
        var period = Objects.requireNonNull(range, "运营范围不能为空").periodAt(generatedAt);
        var tenants = organization.listTenants(page);
        var tenantIds = tenants.items().stream().map(item -> new TenantId(item.tenantId())).toList();
        var queryFacts = queries.tenantSummaries(tenantIds, period.from(), period.to());
        var attention = new HashSet<>(queries.attentionTenants(period.from(), period.to()));
        attention.addAll(models.tenantsWithoutAvailableModel(organization.activeTenantIds()));
        var items = tenants.items().stream().map(tenant -> {
            var tenantId = new TenantId(tenant.tenantId());
            var measured = queryFacts.getOrDefault(tenantId, emptyQuery());
            return new PlatformTenantOperationsSummaryView(
                    Long.toString(tenant.tenantId()), tenant.displayName(), tenant.status(),
                    toQueryView(measured), attention.contains(tenantId));
        }).toList();
        return PageResult.of(items, page, tenants.totalElements());
    }

    private static QueryOperationsView toQueryView(QueryOperationsSummaryFacts facts) {
        return new QueryOperationsView(
                facts.acceptedQueries(), facts.answeredQueries(), facts.refusedQueries(),
                facts.failedQueries(), facts.runningQueries(), facts.totalTokens(),
                OperationsRates.from(
                        facts.acceptedQueries(), facts.answeredQueries(),
                        facts.refusedQueries(), facts.failedQueries()),
                facts.daily().stream().map(point -> new com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryDailyPointView(
                        point.date(), point.accepted(), point.answered(), point.refused(), point.failed(), point.totalTokens()))
                        .toList());
    }

    private static QueryOperationsSummaryFacts emptyQuery() {
        return new QueryOperationsSummaryFacts(0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static List<OperationsNoticeView> notices(
            java.util.Map<String, Long> indexJobs,
            java.util.Map<String, Long> deployments,
            QueryOperationsSummaryFacts query) {
        var notices = new ArrayList<OperationsNoticeView>();
        if (indexJobs.getOrDefault("FAILED", 0L) > 0L) {
            notices.add(new OperationsNoticeView(
                    "CRITICAL", "存在失败的索引任务", "请查看数据与索引状态并重新处理失败任务。", "/data/files"));
        }
        if (!deployments.isEmpty() && deployments.getOrDefault("PUBLISHED", 0L) == 0L) {
            notices.add(new OperationsNoticeView(
                    "WARNING", "当前没有可用模型", "请完成模型验证并发布至少一个部署。", "/models/deployments"));
        }
        if (query.failedQueries() > 0L) {
            notices.add(new OperationsNoticeView(
                    "WARNING", "部分问答未完成", "请查看审计记录确认失败集中时间，并检查模型和数据状态。", "/governance/query"));
        }
        return List.copyOf(notices);
    }
}
