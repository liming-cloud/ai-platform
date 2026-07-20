package com.aiplatform.knowledge.application.retrieval.service;

import com.aiplatform.knowledge.application.command.RetrieveKnowledgeCommand;
import com.aiplatform.knowledge.application.error.KnowledgeErrorCodes;
import com.aiplatform.knowledge.application.retrieval.model.AuthorizationSnapshot;
import com.aiplatform.knowledge.application.retrieval.result.RetrieveKnowledgeResult;
import com.aiplatform.knowledge.application.retrieval.result.RetrievalAuthorizationSnapshot;
import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.answer.KnowledgeAnswerGenerationPort;
import com.aiplatform.knowledge.domain.port.answer.model.KnowledgeAnswerEvidence;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeQueryEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.knowledge.domain.port.indexing.ActiveKnowledgeIndex;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeDocumentVectorIndexRepository;
import com.aiplatform.knowledge.domain.port.retrieval.KnowledgeRetrievalCandidateRepository;
import com.aiplatform.knowledge.domain.port.retrieval.model.RetrievalSearchQuery;
import com.aiplatform.knowledge.domain.retrieval.RetrievalAccessFilter;
import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalEvidenceIdentity;
import com.aiplatform.knowledge.domain.retrieval.policy.KnowledgeRetrievalAccessPolicy;
import com.aiplatform.knowledge.domain.retrieval.service.HybridRetrievalRanker;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 知识检索应用服务，负责服务端向量化、数据库前置召回、ACL 二次过滤、融合排序和可选答案生成。
 */
@Service
public class KnowledgeRetrievalApplicationService {
    private static final String ANSWER_GENERATION_NOT_REQUESTED = "当前请求未指定回答模型，控制面仅返回证据召回结果";

    private final KnowledgeQueryEmbeddingPort embeddingPort;
    private final KnowledgeAnswerGenerationPort answerGenerationPort;
    private final ModelUsagePolicyPort modelUsagePolicyPort;
    private final RbacAuthorizationQueryPort authorizationQueryPort;
    private final KnowledgeBaseRepository knowledgeBases;
    private final KnowledgeDocumentVectorIndexRepository vectorIndex;
    private final KnowledgeRetrievalCandidateRepository candidates;
    private final KnowledgeRetrievalAccessPolicy accessPolicy;
    private final HybridRetrievalRanker ranker;
    private final KnowledgeAnswerGroundingApplicationService answerGroundingService;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    @Autowired
    public KnowledgeRetrievalApplicationService(
            KnowledgeQueryEmbeddingPort embeddingPort,
            KnowledgeAnswerGenerationPort answerGenerationPort,
            ModelUsagePolicyPort modelUsagePolicyPort,
            RbacAuthorizationQueryPort authorizationQueryPort,
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentVectorIndexRepository vectorIndex,
            KnowledgeRetrievalCandidateRepository candidates,
            KnowledgeRetrievalAccessPolicy accessPolicy,
            HybridRetrievalRanker ranker,
            KnowledgeAnswerGroundingApplicationService answerGroundingService,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "查询向量端口不能为空");
        this.answerGenerationPort = Objects.requireNonNull(answerGenerationPort, "答案生成端口不能为空");
        this.modelUsagePolicyPort = Objects.requireNonNull(modelUsagePolicyPort, "模型使用策略端口不能为空");
        this.authorizationQueryPort = Objects.requireNonNull(authorizationQueryPort, "权限快照查询端口不能为空");
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "向量索引仓储不能为空");
        this.candidates = Objects.requireNonNull(candidates, "检索候选仓储不能为空");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "知识检索权限策略不能为空");
        this.ranker = Objects.requireNonNull(ranker, "混合检索排序器不能为空");
        this.answerGroundingService = Objects.requireNonNull(answerGroundingService, "答案落地服务不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public RetrieveKnowledgeResult retrieve(RetrieveKnowledgeCommand command) {
        Objects.requireNonNull(command, "知识检索命令不能为空");
        List<KnowledgeBase> requestedBases = loadRequestedBases(command);
        List<KnowledgeBase> authorizedBases = accessPolicy.authorizedBases(
                command.tenantId(),
                command.principalId(),
                requestedBases
        );
        AuthorizationSnapshot authorizationSnapshot = authorizationSnapshot(command);
        RetrievalAuthorizationSnapshot responseSnapshot = responseSnapshot(command, authorizedBases, authorizationSnapshot);
        int unauthorizedBaseCount = command.knowledgeBaseIds().size() - authorizedBases.size();
        if (authorizedBases.isEmpty()) {
            publishSecurityAudit(command, "SECURITY_RAG_RETRIEVAL_DENIED", "knowledge_base_not_allowed",
                    responseSnapshot, unauthorizedBaseCount);
            return RetrieveKnowledgeResult.noAnswer().withSecurityEvidence(responseSnapshot, unauthorizedBaseCount, 0);
        }
        if (unauthorizedBaseCount > 0) {
            publishSecurityAudit(command, "SECURITY_RAG_KNOWLEDGE_BASE_FILTERED", "partial_knowledge_base_denied",
                    responseSnapshot, unauthorizedBaseCount);
        }
        ActiveKnowledgeIndex activeIndex = resolveActiveIndex(command.tenantId(), authorizedBases);
        String indexVersion = activeIndex.indexVersion();
        KnowledgeModelUsageDecision embeddingModelDecision = decideEmbeddingModelUsage(
                command,
                authorizedBases,
                authorizationSnapshot,
                activeIndex
        );
        if (!embeddingModelDecision.allowed()) {
            publishSecurityAudit(command, "SECURITY_RAG_MODEL_POLICY_DENIED", embeddingModelDecision.reason(),
                    responseSnapshot, unauthorizedBaseCount);
            return new RetrieveKnowledgeResult(false, List.of(), embeddingModelDecision.reason(), null, null, null)
                    .withSecurityEvidence(responseSnapshot, unauthorizedBaseCount, 0);
        }
        List<String> authorizedKnowledgeBaseIds = authorizedBases.stream().map(KnowledgeBase::knowledgeBaseId).toList();
        KnowledgeModelRuntimeDescriptor embeddingRuntimeDescriptor = embeddingModelDecision.runtimeDescriptor();
        float[] queryEmbedding = embeddingPort.embedQuery(command.tenantId(), embeddingRuntimeDescriptor, command.query());
        RetrievalAccessFilter accessFilter = accessPolicy.accessFilter(
                command.tenantId(),
                command.principalId(),
                authorizedBases,
                authorizationSnapshot.roleCodes(),
                authorizationSnapshot.permissionCodes(),
                Instant.now(clock)
        );
        RetrievalSearchQuery query = new RetrievalSearchQuery(
                command.tenantId(),
                authorizedKnowledgeBaseIds,
                command.query(),
                queryEmbedding,
                embeddingRuntimeDescriptor.modelCode(),
                indexVersion,
                accessFilter.aclTokens(),
                accessFilter.now(),
                command.profile().topK(),
                command.profile().scoreThreshold()
        );
        List<RetrievalCandidate> rawVectorCandidates = candidates.searchVectorCandidates(query);
        List<RetrievalCandidate> rawKeywordCandidates = candidates.searchKeywordCandidates(query);
        Set<String> retrievalScope = requestedAuthorizedScope(command, authorizedKnowledgeBaseIds);
        Set<RetrievalEvidenceIdentity> preRankingFilteredIdentities = deniedEvidenceIdentities(
                rawVectorCandidates,
                rawKeywordCandidates,
                retrievalScope,
                indexVersion,
                accessFilter
        );
        List<RetrievalCandidate> vectorCandidates = filterByAccess(
                rawVectorCandidates,
                retrievalScope,
                indexVersion,
                accessFilter,
                preRankingFilteredIdentities
        );
        List<RetrievalCandidate> keywordCandidates = filterByAccess(
                rawKeywordCandidates,
                retrievalScope,
                indexVersion,
                accessFilter,
                preRankingFilteredIdentities
        );
        Map<RetrievalEvidenceIdentity, RetrievalCandidate> authorizedInputs = canonicalCandidates(
                vectorCandidates,
                keywordCandidates
        );
        List<RetrievalCandidate> rankedCandidates = ranker.rank(vectorCandidates, keywordCandidates, command.profile());
        Set<RetrievalEvidenceIdentity> filteredCitationIdentities = new LinkedHashSet<>();
        Map<RetrievalEvidenceIdentity, RetrievalCandidate> citationSafeCandidates = new LinkedHashMap<>();
        for (RetrievalCandidate rankedCandidate : rankedCandidates) {
            RetrievalEvidenceIdentity identity = rankedCandidate.evidenceIdentity();
            RetrievalCandidate canonicalCandidate = authorizedInputs.get(identity);
            if (canonicalCandidate == null
                    || !allowedInScope(canonicalCandidate, retrievalScope, indexVersion, accessFilter)) {
                filteredCitationIdentities.add(identity);
                continue;
            }
            citationSafeCandidates.putIfAbsent(identity, canonicalCandidate);
        }
        Set<RetrievalEvidenceIdentity> allFilteredIdentities = new LinkedHashSet<>(preRankingFilteredIdentities);
        allFilteredIdentities.addAll(filteredCitationIdentities);
        int filteredCitationCount = filteredCitationIdentities.size();
        int filteredCount = unauthorizedBaseCount + allFilteredIdentities.size();
        if (filteredCitationCount > 0) {
            publishSecurityAudit(command, "SECURITY_RAG_CITATION_FILTERED", "citation_acl_denied",
                    responseSnapshot, filteredCitationCount);
        }
        List<RetrievedKnowledgeChunk> ranked = citationSafeCandidates.values().stream()
                .limit(command.profile().topK())
                .map(RetrievedKnowledgeChunk::from)
                .toList();
        if (ranked.isEmpty()) {
            return RetrieveKnowledgeResult.noAnswer()
                    .withSecurityEvidence(responseSnapshot, filteredCount, filteredCitationCount);
        }
        RetrieveKnowledgeResult evidenceResult = RetrieveKnowledgeResult.answerable(ranked)
                .withSecurityEvidence(responseSnapshot, filteredCount, filteredCitationCount);
        if (command.answerModel() == null) {
            return evidenceResult.withAnswerGenerationReason(ANSWER_GENERATION_NOT_REQUESTED);
        }
        KnowledgeModelUsageDecision answerModelDecision = decideAnswerModelUsage(
                command,
                authorizedBases,
                authorizationSnapshot,
                indexVersion
        );
        if (!answerModelDecision.allowed()) {
            publishSecurityAudit(command, "SECURITY_RAG_MODEL_POLICY_DENIED", answerModelDecision.reason(),
                    responseSnapshot, filteredCount);
            return evidenceResult.withAnswerGenerationReason(answerModelDecision.reason());
        }
        KnowledgeModelRuntimeDescriptor answerRuntimeDescriptor = answerModelDecision.runtimeDescriptor();
        String generatedAnswer = answerGenerationPort.generateAnswer(
                command.tenantId(),
                answerRuntimeDescriptor,
                command.query(),
                toAnswerEvidence(ranked)
        );
        String groundedAnswer = answerGroundingService.stabilize(command.query(), ranked, generatedAnswer);
        return evidenceResult.withGeneratedAnswer(groundedAnswer, answerRuntimeDescriptor.modelCode());
    }

    private List<KnowledgeBase> loadRequestedBases(RetrieveKnowledgeCommand command) {
        return command.knowledgeBaseIds().stream()
                .map(knowledgeBaseId -> knowledgeBases.findById(command.tenantId(), knowledgeBaseId))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private ActiveKnowledgeIndex resolveActiveIndex(TenantId tenantId, List<KnowledgeBase> authorizedBases) {
        List<ActiveKnowledgeIndex> activeIndexes = authorizedBases.stream()
                .map(KnowledgeBase::knowledgeBaseId)
                .distinct()
                .map(knowledgeBaseId -> vectorIndex.findActiveIndex(tenantId, knowledgeBaseId)
                        .orElseThrow(() -> new ApplicationException(
                                KnowledgeErrorCodes.RAG_INDEX_NOT_READY,
                                "知识库ID=" + knowledgeBaseId
                        )))
                .distinct()
                .toList();
        if (activeIndexes.size() != 1) {
            throw new IllegalArgumentException("多知识库检索必须使用相同的 active 模型与索引版本");
        }
        return activeIndexes.getFirst();
    }

    private static Set<String> requestedAuthorizedScope(
            RetrieveKnowledgeCommand command,
            List<String> authorizedKnowledgeBaseIds
    ) {
        Set<String> scope = new LinkedHashSet<>(command.knowledgeBaseIds());
        scope.retainAll(authorizedKnowledgeBaseIds);
        return Set.copyOf(scope);
    }

    private static Set<RetrievalEvidenceIdentity> deniedEvidenceIdentities(
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates,
            Set<String> retrievalScope,
            String indexVersion,
            RetrievalAccessFilter filter
    ) {
        Set<RetrievalEvidenceIdentity> denied = new LinkedHashSet<>();
        for (RetrievalCandidate candidate : vectorCandidates) {
            if (!allowedInScope(candidate, retrievalScope, indexVersion, filter)) {
                denied.add(candidate.evidenceIdentity());
            }
        }
        for (RetrievalCandidate candidate : keywordCandidates) {
            if (!allowedInScope(candidate, retrievalScope, indexVersion, filter)) {
                denied.add(candidate.evidenceIdentity());
            }
        }
        return Set.copyOf(denied);
    }

    private static List<RetrievalCandidate> filterByAccess(
            List<RetrievalCandidate> candidates,
            Set<String> retrievalScope,
            String indexVersion,
            RetrievalAccessFilter filter,
            Set<RetrievalEvidenceIdentity> deniedIdentities
    ) {
        return candidates.stream()
                .filter(candidate -> allowedInScope(candidate, retrievalScope, indexVersion, filter))
                .filter(candidate -> !deniedIdentities.contains(candidate.evidenceIdentity()))
                .toList();
    }

    private static boolean allowedInScope(
            RetrievalCandidate candidate,
            Set<String> retrievalScope,
            String indexVersion,
            RetrievalAccessFilter filter
    ) {
        return filter.tenantId().equals(candidate.tenantId())
                && retrievalScope.contains(candidate.knowledgeBaseId())
                && indexVersion.equals(candidate.evidenceIdentity().indexVersion())
                && filter.allows(candidate);
    }

    private static Map<RetrievalEvidenceIdentity, RetrievalCandidate> canonicalCandidates(
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates
    ) {
        Map<RetrievalEvidenceIdentity, RetrievalCandidate> canonical = new LinkedHashMap<>();
        vectorCandidates.forEach(candidate -> canonical.merge(
                candidate.evidenceIdentity(),
                candidate,
                KnowledgeRetrievalApplicationService::strongerCandidate
        ));
        keywordCandidates.forEach(candidate -> canonical.merge(
                candidate.evidenceIdentity(),
                candidate,
                KnowledgeRetrievalApplicationService::strongerCandidate
        ));
        return Map.copyOf(canonical);
    }

    private static RetrievalCandidate strongerCandidate(
            RetrievalCandidate current,
            RetrievalCandidate candidate
    ) {
        if (current.score() != candidate.score()) {
            return current.score() > candidate.score() ? current : candidate;
        }
        return current.rank() <= candidate.rank() ? current : candidate;
    }

    private KnowledgeModelUsageDecision decideEmbeddingModelUsage(
            RetrieveKnowledgeCommand command,
            List<KnowledgeBase> authorizedBases,
            AuthorizationSnapshot authorizationSnapshot,
            ActiveKnowledgeIndex activeIndex
    ) {
        return decideModelUsage(
                command,
                authorizedBases,
                authorizationSnapshot,
                KnowledgeModelCapability.EMBEDDING,
                activeIndex.embeddingModel(),
                Map.of(
                        "indexVersion", activeIndex.indexVersion(),
                        "retrievalProfile", command.profile().name()
                ),
                command.query().length(),
                0
        );
    }

    private KnowledgeModelUsageDecision decideAnswerModelUsage(
            RetrieveKnowledgeCommand command,
            List<KnowledgeBase> authorizedBases,
            AuthorizationSnapshot authorizationSnapshot,
            String indexVersion
    ) {
        return decideModelUsage(
                command,
                authorizedBases,
                authorizationSnapshot,
                KnowledgeModelCapability.CHAT,
                command.answerModel(),
                Map.of(
                        "indexVersion", indexVersion,
                        "retrievalProfile", command.profile().name(),
                        "retrievedChunkCount", authorizedBases.size()
                ),
                estimateAnswerInputTokens(command.query()),
                1024
        );
    }

    private KnowledgeModelUsageDecision decideModelUsage(
            RetrieveKnowledgeCommand command,
            List<KnowledgeBase> authorizedBases,
            AuthorizationSnapshot authorizationSnapshot,
            KnowledgeModelCapability capability,
            String modelConfigId,
            Map<String, Object> requestedOptions,
            long estimatedInputTokens,
            long estimatedOutputTokens
    ) {
        KnowledgeModelUsageDecision firstAllowedDecision = null;
        for (KnowledgeBase knowledgeBase : authorizedBases) {
            KnowledgeModelUsageDecision decision = modelUsagePolicyPort.decide(new KnowledgeModelUsageRequest(
                    command.tenantId(),
                    command.principalId(),
                    command.principalType(),
                    authorizationSnapshot.roleCodes(),
                    authorizationSnapshot.permissionCodes(),
                    knowledgeBase.knowledgeBaseId(),
                    KnowledgeModelUseCase.RAG_QUERY,
                    capability,
                    modelConfigId,
                    requestedOptions,
                    estimatedInputTokens,
                    estimatedOutputTokens
            ));
            if (!decision.allowed()) {
                return decision;
            }
            if (firstAllowedDecision == null) {
                firstAllowedDecision = decision;
            }
        }
        return firstAllowedDecision == null
                ? KnowledgeModelUsageDecision.deny("knowledge_base_not_allowed", "没有可用知识库模型策略")
                : firstAllowedDecision;
    }

    private AuthorizationSnapshot authorizationSnapshot(RetrieveKnowledgeCommand command) {
        Set<String> roleCodes = authorizationQueryPort.findRoleCodes(
                command.tenantId(),
                command.principalType(),
                command.principalId()
        );
        return new AuthorizationSnapshot(
                roleCodes,
                authorizationQueryPort.findPermissionCodes(command.tenantId(), command.principalType(), command.principalId())
        );
    }

    private static RetrievalAuthorizationSnapshot responseSnapshot(
            RetrieveKnowledgeCommand command,
            List<KnowledgeBase> authorizedBases,
            AuthorizationSnapshot authorizationSnapshot
    ) {
        return RetrievalAuthorizationSnapshot.of(
                authorizationSnapshot.roleCodes(),
                authorizationSnapshot.permissionCodes().stream().map(permission -> permission.value()).toList(),
                command.knowledgeBaseIds(),
                authorizedBases.stream().map(KnowledgeBase::knowledgeBaseId).toList()
        );
    }

    private void publishSecurityAudit(
            RetrieveKnowledgeCommand command,
            String action,
            String reason,
            RetrievalAuthorizationSnapshot snapshot,
            int filteredCount
    ) {
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                command.tenantId().value(),
                command.principalId(),
                "RAG_RETRIEVAL",
                String.join(",", snapshot.requestedKnowledgeBaseIds()),
                action,
                "HIGH",
                null,
                TraceContext.currentTraceId(),
                Instant.now(clock),
                Map.of(
                        "reason", reason,
                        "requestedKnowledgeBaseIds", snapshot.requestedKnowledgeBaseIds(),
                        "authorizedKnowledgeBaseIds", snapshot.authorizedKnowledgeBaseIds(),
                        "filteredCount", filteredCount
                )
        ));
    }

    private static List<KnowledgeAnswerEvidence> toAnswerEvidence(List<RetrievedKnowledgeChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new KnowledgeAnswerEvidence(
                        chunk.knowledgeBaseId(),
                        chunk.documentId(),
                        chunk.chunkId(),
                        chunk.content(),
                        chunk.sourceUri(),
                        chunk.titlePath(),
                        chunk.paragraphNo(),
                        chunk.tableCellRange(),
                        chunk.authoritative()
                ))
                .toList();
    }

    private static long estimateAnswerInputTokens(String query) {
        return Math.max(256, query.length() * 6L);
    }
}
