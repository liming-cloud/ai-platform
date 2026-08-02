package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.AssistantTestQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.AssistantTestQuotaLimitType;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.AssistantTestRunConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto.AssistantTestRunTerminalCommit;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.port.out.AssistantTestRunTerminalCommitter;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestCitation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestQuotaSnapshot;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRefusalReason;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRequesterId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRun;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestTarget;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestUsage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantTestCitationEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantTestRunEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantTestRunModelInvocationTraceEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestCitationMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestRunMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestRunModelInvocationTraceMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 以短事务串行占用独立草稿测试配额，并只保存最小运行事实。
 */
public final class PostgresAssistantTestRunRepository
        implements AssistantTestRunRepository, AssistantTestRunTerminalCommitter {

    private final AssistantTestRunMapper mapper;
    private final AssistantTestCitationMapper citationMapper;
    private final AssistantTestRunModelInvocationTraceMapper traceMapper;
    private final EntityIdGenerator identifiers;
    private final TransactionTemplate transactions;

    public PostgresAssistantTestRunRepository(
            AssistantTestRunMapper mapper,
            AssistantTestCitationMapper citationMapper,
            AssistantTestRunModelInvocationTraceMapper traceMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "助手测试运行映射器不能为空");
        this.citationMapper = Objects.requireNonNull(citationMapper, "助手测试引用映射器不能为空");
        this.traceMapper = Objects.requireNonNull(traceMapper, "助手测试调用轨迹映射器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public AssistantTestRun claim(AssistantTestRun proposed, AssistantTestQuotaPolicy quota) {
        Objects.requireNonNull(proposed, "助手测试运行不能为空");
        Objects.requireNonNull(quota, "助手测试配额策略不能为空");
        if (proposed.quota() != null || proposed.status() != AssistantTestRunStatus.RUNNING) {
            throw new IllegalArgumentException("只能占用尚未接受配额的运行中助手测试");
        }
        var result = transactions.execute(status -> claimInTransaction(proposed, quota));
        return Objects.requireNonNull(result, "助手测试配额占用事务没有返回结果");
    }

    @Override
    public void complete(AssistantTestRun run, long expectedVersion) {
        commit(new AssistantTestRunTerminalCommit(run, expectedVersion, null));
    }

    @Override
    public void commit(AssistantTestRunTerminalCommit terminal) {
        Objects.requireNonNull(terminal, "助手测试终态提交不能为空");
        var run = terminal.run();
        if (run.status() == AssistantTestRunStatus.RUNNING) {
            throw new IllegalArgumentException("不能把运行中助手测试作为终态提交");
        }
        var result = transactions.execute(status -> {
            var usage = run.usage();
            var updated = mapper.completeRun(
                    run.tenantId().value(),
                    run.id().value(),
                    run.status().name(),
                    run.refusalReason() == null ? null : run.refusalReason().name(),
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.totalTokens(),
                    run.version(),
                    run.completedAt(),
                    terminal.expectedVersion());
            if (updated != 1) {
                throw new AssistantTestRunConcurrentModificationException();
            }
            for (var citation : run.citations()) {
                citationMapper.insert(toCitationEntity(run, citation));
            }
            insertTrace(run, terminal);
            return Boolean.TRUE;
        });
        Objects.requireNonNull(result, "助手测试终态提交事务没有返回结果");
    }

    private void insertTrace(AssistantTestRun run, AssistantTestRunTerminalCommit terminal) {
        var trace = terminal.modelInvocationTrace();
        if (trace == null) {
            return;
        }
        for (var attempt : trace.attempts()) {
            var entity = new AssistantTestRunModelInvocationTraceEntity();
            entity.setId(identifiers.nextId());
            entity.setTenantId(run.tenantId().value());
            entity.setAssistantTestRunId(run.id().value());
            entity.setAttemptSequence(attempt.sequence());
            entity.setConfigurationId(attempt.configurationId());
            entity.setConfigurationRevision(attempt.configurationRevision());
            entity.setOutcome(attempt.outcome().name());
            entity.setCreatedAt(run.completedAt());
            if (traceMapper.insert(entity) != 1) {
                throw new IllegalStateException("助手测试调用轨迹写入失败");
            }
        }
    }

    @Override
    public Optional<AssistantTestRun> find(TenantId tenantId, AssistantTestRunId runId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(runId, "助手测试运行标识不能为空");
        var row = mapper.selectOne(Wrappers.<AssistantTestRunEntity>lambdaQuery()
                .eq(AssistantTestRunEntity::getTenantId, tenantId.value())
                .eq(AssistantTestRunEntity::getId, runId.value()));
        return Optional.ofNullable(row).map(this::toRun);
    }

    private AssistantTestRun claimInTransaction(
            AssistantTestRun proposed,
            AssistantTestQuotaPolicy quota) {
        var tenantId = proposed.tenantId().value();
        // 同一租户的计数、租约与插入必须串行，避免并发测试共同越过独立限额。
        mapper.lockScope("assistant-test-quota:" + tenantId);
        var proposedQuota = AssistantTestQuotaSnapshot.forAcceptedTest(
                proposed.createdAt(), quota, 1);
        var dailyCount = Math.toIntExact(mapper.selectCount(
                Wrappers.<AssistantTestRunEntity>lambdaQuery()
                        .eq(AssistantTestRunEntity::getTenantId, tenantId)
                        .ge(AssistantTestRunEntity::getCreatedAt, proposedQuota.periodStart())
                        .lt(AssistantTestRunEntity::getCreatedAt, proposedQuota.periodEnd())));
        if (dailyCount >= quota.dailyTestLimit()) {
            throw new AssistantTestQuotaExceededException(
                    AssistantTestQuotaLimitType.DAILY,
                    quota.dailyTestLimit(),
                    proposedQuota.periodEnd());
        }
        var activeLeases = Math.toIntExact(mapper.selectCount(
                Wrappers.<AssistantTestRunEntity>lambdaQuery()
                        .eq(AssistantTestRunEntity::getTenantId, tenantId)
                        .eq(AssistantTestRunEntity::getStatus, AssistantTestRunStatus.RUNNING.name())
                        .gt(AssistantTestRunEntity::getAcceptedLeaseUntil, proposed.createdAt())));
        if (activeLeases >= quota.concurrentTestLimit()) {
            var retryAt = mapper.findEarliestActiveLeaseExpiry(tenantId, proposed.createdAt());
            throw new AssistantTestQuotaExceededException(
                    AssistantTestQuotaLimitType.CONCURRENT,
                    quota.concurrentTestLimit(),
                    Objects.requireNonNull(retryAt, "有效助手测试租约缺少截止时间"));
        }
        var accepted = proposed.acceptQuota(AssistantTestQuotaSnapshot.forAcceptedTest(
                proposed.createdAt(), quota, dailyCount + 1));
        mapper.insert(toEntity(accepted));
        return accepted;
    }

    private AssistantTestRun toRun(AssistantTestRunEntity row) {
        return AssistantTestRun.reconstitute(
                new TenantId(row.getTenantId()),
                new AssistantTestRunId(row.getId()),
                new AssistantTestRequesterId(row.getRequestedBy()),
                new AssistantTestTarget(
                        row.getAssistantVersionId(),
                        row.getKnowledgeSpaceId(),
                        row.getIndexVersionId(),
                        row.getIndexVersionRevision(),
                        new GovernedChatModelBinding(
                                row.getChatConfigurationId(),
                                row.getChatConfigurationRevision(),
                                row.getChatCatalogEntryId(),
                                row.getChatCatalogEntryRevision(),
                                row.getChatOutputLimit()),
                        new GovernedEmbeddingModelBinding(
                                row.getEmbeddingConfigurationId(),
                                row.getEmbeddingConfigurationRevision(),
                                row.getEmbeddingCatalogEntryId(),
                                row.getEmbeddingCatalogEntryRevision(),
                                row.getEmbeddingDimension()),
                        row.getSystemInstruction(),
                        row.getMaxOutputTokens(),
                        row.getRetrievalLimit(),
                        row.getMinimumScore(),
                        CitationPolicy.valueOf(row.getCitationPolicy()),
                        RefusalPolicy.valueOf(row.getRefusalPolicy())),
                row.getQuestionDigest(),
                new AssistantTestQuotaSnapshot(
                        row.getQuotaPeriodStart(),
                        row.getQuotaPeriodEnd(),
                        row.getDailyTestLimit(),
                        row.getDailyTestOrdinal(),
                        row.getConcurrentTestLimit(),
                        row.getAcceptedLeaseUntil()),
                AssistantTestRunStatus.valueOf(row.getStatus()),
                row.getRefusalReason() == null
                        ? null
                        : AssistantTestRefusalReason.valueOf(row.getRefusalReason()),
                findCitations(row.getTenantId(), row.getId()),
                new AssistantTestUsage(row.getInputTokens(), row.getOutputTokens()),
                row.getVersion(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }

    private List<AssistantTestCitation> findCitations(long tenantId, long runId) {
        return citationMapper.selectList(Wrappers.<AssistantTestCitationEntity>lambdaQuery()
                        .eq(AssistantTestCitationEntity::getTenantId, tenantId)
                        .eq(AssistantTestCitationEntity::getAssistantTestRunId, runId)
                        .orderByAsc(AssistantTestCitationEntity::getSequence))
                .stream()
                .map(row -> new AssistantTestCitation(
                        row.getSequence(),
                        row.getDataSourceId(),
                        row.getContentVersion(),
                        row.getChunkId(),
                        row.getOrdinal()))
                .toList();
    }

    private static AssistantTestRunEntity toEntity(AssistantTestRun run) {
        var target = run.target();
        var quota = Objects.requireNonNull(run.quota(), "助手测试配额快照不能为空");
        var entity = new AssistantTestRunEntity();
        entity.setId(run.id().value());
        entity.setTenantId(run.tenantId().value());
        entity.setRequestedBy(run.requestedBy().value());
        entity.setAssistantVersionId(target.assistantVersionId());
        entity.setKnowledgeSpaceId(target.knowledgeSpaceId());
        entity.setIndexVersionId(target.indexVersionId());
        entity.setIndexVersionRevision(target.indexVersionRevision());
        entity.setChatConfigurationId(target.governedChat().configurationId());
        entity.setChatConfigurationRevision(target.governedChat().configurationRevision());
        entity.setChatCatalogEntryId(target.governedChat().catalogEntryId());
        entity.setChatCatalogEntryRevision(target.governedChat().catalogEntryRevision());
        entity.setChatOutputLimit(target.governedChat().outputLimit());
        entity.setEmbeddingConfigurationId(target.governedEmbedding().configurationId());
        entity.setEmbeddingConfigurationRevision(target.governedEmbedding().configurationRevision());
        entity.setEmbeddingCatalogEntryId(target.governedEmbedding().catalogEntryId());
        entity.setEmbeddingCatalogEntryRevision(target.governedEmbedding().catalogEntryRevision());
        entity.setEmbeddingDimension(target.governedEmbedding().dimension());
        entity.setSystemInstruction(target.systemInstruction());
        entity.setMaxOutputTokens(target.maxOutputTokens());
        entity.setRetrievalLimit(target.retrievalLimit());
        entity.setMinimumScore(target.minimumScore());
        entity.setCitationPolicy(target.citationPolicy().name());
        entity.setRefusalPolicy(target.refusalPolicy().name());
        entity.setQuestionDigest(run.questionDigest());
        entity.setQuotaPeriodStart(quota.periodStart());
        entity.setQuotaPeriodEnd(quota.periodEnd());
        entity.setDailyTestLimit(quota.dailyLimit());
        entity.setDailyTestOrdinal(quota.dailyOrdinal());
        entity.setConcurrentTestLimit(quota.concurrentLimit());
        entity.setAcceptedLeaseUntil(quota.acceptedLeaseUntil());
        entity.setStatus(run.status().name());
        entity.setInputTokens(run.usage().inputTokens());
        entity.setOutputTokens(run.usage().outputTokens());
        entity.setTotalTokens(run.usage().totalTokens());
        entity.setVersion(run.version());
        entity.setCreatedAt(run.createdAt());
        return entity;
    }

    private AssistantTestCitationEntity toCitationEntity(
            AssistantTestRun run,
            AssistantTestCitation citation) {
        var entity = new AssistantTestCitationEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(run.tenantId().value());
        entity.setAssistantTestRunId(run.id().value());
        entity.setSequence(citation.sequence());
        entity.setDataSourceId(citation.dataSourceId());
        entity.setContentVersion(citation.contentVersion());
        entity.setChunkId(citation.chunkId());
        entity.setOrdinal(citation.ordinal());
        return entity;
    }
}
