package com.aiplatform.foundation.trustedquery.application;

import com.aiplatform.foundation.assistantdelivery.api.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.api.model.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.api.model.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingRequest;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunResult;
import com.aiplatform.foundation.trustedquery.api.model.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryCitationView;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunView;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * 按冻结助手版本执行权限、检索、生成、引用和计量的固定流水线。
 */
public final class DefaultQueryRunService implements QueryRunUseCase {

    private static final PermissionCode QUERY = new PermissionCode("assistant:query");
    private static final PermissionCode KNOWLEDGE_READ =
            new PermissionCode("knowledge:read");

    private final PublishedAssistantVersionLookup assistants;
    private final ModelRuntimeResolver models;
    private final IndexCandidateRetriever candidates;
    private final QueryRunRepository repository;
    private final QueryQuotaPolicy quota;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public DefaultQueryRunService(
            PublishedAssistantVersionLookup assistants,
            ModelRuntimeResolver models,
            IndexCandidateRetriever candidates,
            QueryRunRepository repository,
            QueryQuotaPolicy quota,
            Clock clock,
            Supplier<UUID> identifiers) {
        this.assistants = Objects.requireNonNull(
                assistants,
                "已发布助手版本查询不能为空");
        this.models = Objects.requireNonNull(models, "模型运行时解析器不能为空");
        this.candidates = Objects.requireNonNull(candidates, "候选检索器不能为空");
        this.repository = Objects.requireNonNull(repository, "查询运行仓储不能为空");
        this.quota = Objects.requireNonNull(quota, "查询配额策略不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    @Override
    public CreateQueryRunResult create(CreateQueryRunCommand command) {
        Objects.requireNonNull(command, "创建查询运行命令不能为空");
        requirePermissions(command.context());
        var assistant = assistants
                .find(
                        command.context().tenantId(),
                        command.assistantVersionId())
                .orElseThrow(QueryAssistantUnavailableException::new);
        var started = QueryRun.start(
                command.context().tenantId(),
                identifiers.get(),
                assistant.assistantVersionId(),
                command.context().membershipId(),
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
        final QueryRun completed;
        try {
            completed = execute(
                    command.context(),
                    command.question(),
                    assistant,
                    claim.queryRun());
        } catch (RuntimeException exception) {
            repository.complete(
                    claim.queryRun().fail(clock.instant()),
                    claim.queryRun().version());
            throw exception;
        }
        repository.complete(completed, claim.queryRun().version());
        return new CreateQueryRunResult(toView(completed), false);
    }

    @Override
    public QueryRunView get(GetQueryRunQuery query) {
        Objects.requireNonNull(query, "查询运行读取请求不能为空");
        requirePermissions(query.context());
        return repository
                .find(query.context().tenantId(), query.queryRunId())
                .map(DefaultQueryRunService::toView)
                .orElseThrow(QueryRunNotFoundException::new);
    }

    private QueryRun execute(
            TrustedRequestContext context,
            String question,
            PublishedAssistantVersionSnapshot assistant,
            QueryRun started) {
        var embeddingReference = new EmbeddingDeploymentRef(
                assistant.embeddingDeploymentId(),
                assistant.embeddingDeploymentRevision(),
                assistant.embeddingDimension());
        var embeddingModel = models.resolveEmbedding(
                context.tenantId(),
                assistant.embeddingDeploymentId());
        var embedded = embeddingModel.embed(new EmbeddingRequest(
                embeddingReference,
                List.of(question)));
        if (!embeddingReference.equals(embedded.deployment())
                || embedded.vectors().size() != 1
                || embedded.vectors().getFirst().index() != 0) {
            throw new IllegalStateException("查询嵌入结果不符合助手冻结部署");
        }
        var retrieved = candidates.retrieve(new IndexCandidateQuery(
                        context.tenantId().value(),
                        assistant.knowledgeSpaceId(),
                        assistant.indexVersionId(),
                        assistant.embeddingDeploymentId(),
                        assistant.embeddingDeploymentRevision(),
                        embedded.vectors().getFirst().values(),
                        QueryEvidenceSelector.candidateLimit(
                                assistant.retrievalLimit())));
        var authorized = QueryEvidenceSelector.select(
                question,
                retrieved,
                assistant.retrievalLimit(),
                assistant.minimumScore());
        if (authorized.isEmpty()) {
            return started.refuse(
                    QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                    clock.instant());
        }
        return generate(context, question, assistant, started, authorized);
    }

    private QueryRun generate(
            TrustedRequestContext context,
            String question,
            PublishedAssistantVersionSnapshot assistant,
            QueryRun started,
            List<IndexCandidate> evidence) {
        var deployment = new ChatDeploymentRef(
                assistant.chatDeploymentId(),
                assistant.chatDeploymentRevision());
        var chat = models.resolveChat(
                context.tenantId(),
                assistant.chatDeploymentId());
        var result = chat.chat(new ChatRequest(
                deployment,
                QueryPromptBuilder.build(assistant, question, evidence),
                assistant.maxOutputTokens()));
        if (!deployment.equals(result.deployment())) {
            throw new IllegalStateException("对话结果不符合助手冻结部署");
        }
        var citations = IntStream.range(0, evidence.size())
                .mapToObj(index -> citation(index + 1, evidence.get(index)))
                .toList();
        var answer = requireGeneratedAnswer(result.content())
                + "\n\n引用："
                + IntStream.rangeClosed(1, citations.size())
                        .mapToObj(index -> "[" + index + "]")
                        .reduce((left, right) -> left + " " + right)
                        .orElseThrow();
        return started.answer(
                answer,
                citations,
                result.usage(),
                clock.instant());
    }

    private static QueryCitation citation(int sequence, IndexCandidate candidate) {
        return new QueryCitation(
                sequence,
                candidate.dataSourceId(),
                candidate.contentVersion(),
                candidate.chunkId(),
                candidate.ordinal(),
                QueryPromptBuilder.quote(candidate.content()),
                candidate.score());
    }

    private static String requireGeneratedAnswer(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("模型没有返回可用回答");
        }
        return value.strip();
    }

    private static void requirePermissions(TrustedRequestContext context) {
        if (!context.hasPermission(QUERY)
                || !context.hasPermission(KNOWLEDGE_READ)) {
            throw new CandidateSearchPermissionDeniedException();
        }
    }

    private static QueryRunView toView(QueryRun queryRun) {
        return new QueryRunView(
                queryRun.id(),
                queryRun.assistantVersionId(),
                queryRun.status().name(),
                queryRun.answer(),
                queryRun.refusalReason() == null
                        ? null
                        : queryRun.refusalReason().name(),
                queryRun.citations().stream()
                        .map(value -> new QueryCitationView(
                                value.sequence(),
                                value.dataSourceId(),
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

    private static String fingerprint(UUID assistantVersionId, String question) {
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
}
