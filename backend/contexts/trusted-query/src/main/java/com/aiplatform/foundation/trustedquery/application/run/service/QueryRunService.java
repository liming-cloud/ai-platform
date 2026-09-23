package com.aiplatform.foundation.trustedquery.application.run.service;

import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CandidateCollection;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CitationValidation;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryDiagnosticFact;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryRunTerminalCommit;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryRunTerminalCommitter;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerCitation;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerExecutionObservation;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerResult;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerTarget;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerExecutionException;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerFacade;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateRetriever;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.EmbeddingConfiguration;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.DefaultChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.run.command.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.application.run.dto.CreateQueryRunResult;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryAssistantUnavailableException;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunInProgressException;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunNotFoundException;
import com.aiplatform.foundation.trustedquery.application.run.query.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryCitationView;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryRunView;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 按冻结助手版本执行权限、检索、生成、引用和计量的固定流水线。
 */
public final class QueryRunService  {

    private static final PermissionCode QUERY = new PermissionCode("assistant:query");
    private static final PermissionCode KNOWLEDGE_READ =
            new PermissionCode("knowledge:read");

    private final PublishedAssistantVersionLookup assistants;
    private final AssistantTrustedAnswerFacade executor;
    private final QueryRunRepository repository;
    private final QueryRunTerminalCommitter terminalCommitter;
    private final QueryQuotaPolicy quota;
    private final Clock clock;
    private final EntityIdGenerator identifiers;

    public QueryRunService(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            IndexCandidateRetriever candidates,
            QueryRunRepository repository,
            QueryRunTerminalCommitter terminalCommitter,
            QueryQuotaPolicy quota,
            Clock clock,
            EntityIdGenerator identifiers) {
        this(
                assistants, models, null, null, null, null, candidates, repository, terminalCommitter,
                quota, clock, identifiers);
    }

    public QueryRunService(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            UsableModelConfigurationLookup modelConfigurations,
            GovernedEmbeddingRuntime governedEmbeddings,
            GovernedChatRuntime governedChats,
            IndexCandidateRetriever candidates,
            QueryRunRepository repository,
            QueryRunTerminalCommitter terminalCommitter,
            QueryQuotaPolicy quota,
            Clock clock,
            EntityIdGenerator identifiers) {
        this(
                assistants, models, modelConfigurations, governedEmbeddings, governedChats, null,
                candidates, repository, terminalCommitter, quota, clock, identifiers);
    }

    public QueryRunService(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            UsableModelConfigurationLookup modelConfigurations,
            GovernedEmbeddingRuntime governedEmbeddings,
            GovernedChatRuntime governedChats,
            ChatRouteExecutor chatRoutes,
            IndexCandidateRetriever candidates,
            QueryRunRepository repository,
            QueryRunTerminalCommitter terminalCommitter,
            QueryQuotaPolicy quota,
            Clock clock,
            EntityIdGenerator identifiers) {
        this.assistants = Objects.requireNonNull(
                assistants,
                "已发布助手版本查询不能为空");
        this.executor = new AssistantTrustedAnswerFacade(
                Objects.requireNonNull(models, "模型运行时解析器不能为空"),
                modelConfigurations, governedEmbeddings, governedChats,
                chatRoutes == null && governedChats != null
                        ? new DefaultChatRouteExecutor(governedChats)
                        : chatRoutes,
                Objects.requireNonNull(candidates, "候选检索器不能为空"));
        this.repository = Objects.requireNonNull(repository, "查询运行仓储不能为空");
        this.terminalCommitter = Objects.requireNonNull(terminalCommitter, "查询终态提交器不能为空");
        this.quota = Objects.requireNonNull(quota, "查询配额策略不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    public CreateQueryRunResult create(CreateQueryRunCommand command) {
        Objects.requireNonNull(command, "创建查询运行命令不能为空");
        requirePermissions(command.context());
        var assistant = assistants
                .find(
                        command.context().tenantId(),
                        parsePositiveIdentifier(
                                command.assistantVersionId(),
                                "助手版本标识"))
                .orElseThrow(QueryAssistantUnavailableException::new);
        var started = QueryRun.start(
                command.context().tenantId(),
                new QueryRunId(identifiers.nextId()),
                assistant.assistantVersionId(),
                new QueryRequesterId(command.context().membershipId().value()),
                digest(command.question()),
                clock.instant());
        var claim = repository.claim(
                started,
                command.idempotencyKey(),
                fingerprint(command.assistantVersionId(), command.question()),
                quota);
        if (claim.state() == QueryRunClaimState.REPLAYED) {
            return new CreateQueryRunResult(toView(claim.queryRun()), true);
        }
        if (claim.state() == QueryRunClaimState.IN_PROGRESS) {
            throw new QueryRunInProgressException();
        }
        var target = AssistantTrustedAnswerTarget.from(assistant);
        final QueryRunTerminalCommit completed;
        try {
            completed = execute(
                    command.context(),
                    command.question(),
                    target,
                    claim.queryRun());
        } catch (AssistantTrustedAnswerExecutionException exception) {
            terminalCommitter.commit(failed(
                    claim.queryRun(),
                    exception.observation(),
                    claim.queryRun().version()));
            throw exception;
        } catch (RuntimeException exception) {
            terminalCommitter.commit(failed(
                    claim.queryRun(),
                    new AssistantTrustedAnswerExecutionObservation(
                            null, chatRevision(target), embeddingRevision(target)),
                    claim.queryRun().version()));
            throw exception;
        }
        terminalCommitter.commit(completed);
        return new CreateQueryRunResult(toView(completed.queryRun()), false);
    }

    public QueryRunView get(GetQueryRunQuery query) {
        Objects.requireNonNull(query, "查询运行读取请求不能为空");
        requirePermissions(query.context());
        return repository
                .find(
                        query.context().tenantId(),
                        QueryRunId.parse(query.queryRunId()))
                .map(QueryRunService::toView)
                .orElseThrow(QueryRunNotFoundException::new);
    }

    private QueryRunTerminalCommit execute(
            TrustedRequestContext context,
            String question,
            AssistantTrustedAnswerTarget target,
            QueryRun started) {
        var result = executor.execute(context, target, question);
        try {
            if (result.outcome() == AssistantTrustedAnswerResult.Outcome.REFUSED_INSUFFICIENT_EVIDENCE) {
                var refused = started.refuse(QueryRefusalReason.INSUFFICIENT_EVIDENCE, clock.instant());
                return new QueryRunTerminalCommit(refused, started.version(), QueryDiagnosticFact.forTerminal(
                        refused.status(), CandidateCollection.COLLECTED, result.candidateCount(),
                        result.chatModelRevision(), result.embeddingModelRevision(), CitationValidation.NOT_APPLICABLE));
            }
            var answered = started.answer(
                    result.answer(), result.citations().stream().map(QueryRunService::citation).toList(),
                    new QueryModelUsage(
                            Math.toIntExact(result.usage().inputTokens()),
                            Math.toIntExact(result.usage().outputTokens()),
                            Math.toIntExact(result.usage().totalTokens())),
                    clock.instant());
            return new QueryRunTerminalCommit(answered, started.version(), QueryDiagnosticFact.forTerminal(
                    answered.status(), CandidateCollection.COLLECTED, result.candidateCount(),
                    result.chatModelRevision(), result.embeddingModelRevision(), CitationValidation.VALIDATED),
                    result.modelInvocationTrace());
        } catch (RuntimeException exception) {
            throw new AssistantTrustedAnswerExecutionException(
                    new AssistantTrustedAnswerExecutionObservation(
                            result.candidateCount(),
                            result.chatModelRevision(),
                            result.embeddingModelRevision(),
                            result.modelInvocationTrace()),
                    exception);
        }
    }

    private QueryRunTerminalCommit failed(
            QueryRun started,
            AssistantTrustedAnswerExecutionObservation observation,
            long expectedVersion) {
        var failed = started.fail(clock.instant());
        return new QueryRunTerminalCommit(failed, expectedVersion, QueryDiagnosticFact.forTerminal(
                failed.status(), observation.candidateCount() == null
                        ? CandidateCollection.NOT_COLLECTED
                        : CandidateCollection.COLLECTED,
                observation.candidateCount(), observation.chatModelRevision(), observation.embeddingModelRevision(),
                CitationValidation.NOT_APPLICABLE), observation.modelInvocationTrace());
    }

    private static long chatRevision(AssistantTrustedAnswerTarget target) {
        return target.usesGovernedModelConfigurations()
                ? target.governedChat().configurationRevision()
                : target.chatDeploymentRevision();
    }

    private static long embeddingRevision(AssistantTrustedAnswerTarget target) {
        return target.usesGovernedModelConfigurations()
                ? target.governedEmbedding().configurationRevision()
                : target.embeddingDeploymentRevision();
    }

    private static QueryCitation citation(AssistantTrustedAnswerCitation candidate) {
        return new QueryCitation(
                candidate.sequence(), candidate.dataSourceId(), candidate.contentVersion(),
                candidate.chunkId(), candidate.ordinal(), candidate.quote(), candidate.score());
    }

    private static void requirePermissions(TrustedRequestContext context) {
        if (!context.hasPermission(QUERY)
                || !context.hasPermission(KNOWLEDGE_READ)) {
            throw new CandidateSearchPermissionDeniedException();
        }
    }

    private static QueryRunView toView(QueryRun queryRun) {
        return new QueryRunView(
                queryRun.id().toString(),
                Long.toString(queryRun.assistantVersionId()),
                queryRun.status().name(),
                queryRun.answer(),
                queryRun.refusalReason() == null
                        ? null
                        : queryRun.refusalReason().name(),
                queryRun.citations().stream()
                        .map(value -> new QueryCitationView(
                                value.sequence(),
                                Long.toString(value.dataSourceId()),
                                value.contentVersion(),
                                value.chunkId(),
                                value.ordinal(),
                                value.quote(),
                                value.score()))
                        .toList(),
                queryRun.usage(),
                queryRun.version(),
                queryRun.createdAt(),
                queryRun.completedAt());
    }

    private static String digest(String value) {
        var digest = sha256();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fingerprint(String assistantVersionId, String question) {
        var digest = sha256();
        update(digest, assistantVersionId.toString());
        update(digest, question);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static long parsePositiveIdentifier(String value, String field) {
        Objects.requireNonNull(value, field + "不能为空");
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(field + "必须是正整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + "格式不正确", exception);
        }
    }
}
