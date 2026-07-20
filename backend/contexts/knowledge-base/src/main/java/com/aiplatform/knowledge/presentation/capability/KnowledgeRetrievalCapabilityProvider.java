package com.aiplatform.knowledge.presentation.capability;

import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCapability;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.knowledge.application.command.RetrieveKnowledgeCommand;
import com.aiplatform.knowledge.application.retrieval.result.RetrieveKnowledgeResult;
import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import com.aiplatform.knowledge.application.retrieval.service.KnowledgeRetrievalApplicationService;
import com.aiplatform.knowledge.domain.retrieval.RetrievalEvidenceIdentity;
import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * knowledge-base 开放能力提供入口。
 * 该入口实现 capability-contracts，不暴露 application service、domain、repository 或 HTTP schema 作为跨上下文主契约。
 */
@Component
public class KnowledgeRetrievalCapabilityProvider implements KnowledgeRetrievalCapability {
    private static final String NO_AUTHORIZED_EVIDENCE = "未召回到具备权限且可信的知识片段";

    private final KnowledgeRetrievalApplicationService retrievalService;

    public KnowledgeRetrievalCapabilityProvider(KnowledgeRetrievalApplicationService retrievalService) {
        this.retrievalService = Objects.requireNonNull(retrievalService, "知识检索应用服务不能为空");
    }

    @Override
    public KnowledgeRetrievalResponse retrieve(KnowledgeRetrievalRequest request) {
        Objects.requireNonNull(request, "知识检索能力请求不能为空");
        RetrieveKnowledgeResult result = retrievalService.retrieve(new RetrieveKnowledgeCommand(
                request.tenantId(),
                request.principalId(),
                request.principalType(),
                request.knowledgeBaseIds(),
                request.query(),
                resolveProfile(request.retrievalProfile()),
                null
        ));
        return toCapabilityResponse(
                result,
                request.tenantId().value(),
                request.knowledgeBaseIds()
        );
    }

    private static KnowledgeRetrievalResponse toCapabilityResponse(
            RetrieveKnowledgeResult result,
            String tenantId,
            List<String> requestedKnowledgeBaseIds
    ) {
        if (!result.answerable()) {
            return new KnowledgeRetrievalResponse(
                    false,
                    List.of(),
                    List.of(),
                    result.noAnswerReason()
            );
        }
        Set<String> allowedKnowledgeBaseIds = new LinkedHashSet<>(
                result.authorizationSnapshot().authorizedKnowledgeBaseIds()
        );
        allowedKnowledgeBaseIds.retainAll(Set.copyOf(requestedKnowledgeBaseIds));
        Set<String> scopedIndexVersions = result.chunks().stream()
                .filter(chunk -> tenantId.equals(chunk.tenantId()))
                .filter(chunk -> allowedKnowledgeBaseIds.contains(chunk.knowledgeBaseId()))
                .map(RetrievedKnowledgeChunk::indexVersion)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (scopedIndexVersions.size() != 1) {
            return new KnowledgeRetrievalResponse(
                    false,
                    List.of(),
                    List.of(),
                    NO_AUTHORIZED_EVIDENCE
            );
        }
        String indexVersion = scopedIndexVersions.iterator().next();
        Map<RetrievalEvidenceIdentity, RetrievedKnowledgeChunk> safeChunks = new LinkedHashMap<>();
        for (RetrievedKnowledgeChunk chunk : result.chunks()) {
            RetrievalEvidenceIdentity identity = chunk.evidenceIdentity();
            if (tenantId.equals(identity.tenantId())
                    && allowedKnowledgeBaseIds.contains(identity.knowledgeBaseId())
                    && indexVersion.equals(identity.indexVersion())) {
                safeChunks.putIfAbsent(identity, chunk);
            }
        }
        if (safeChunks.isEmpty()) {
            return new KnowledgeRetrievalResponse(
                    false,
                    List.of(),
                    List.of(),
                    NO_AUTHORIZED_EVIDENCE
            );
        }
        List<RetrievedKnowledgeChunk> outputChunks = List.copyOf(safeChunks.values());
        return new KnowledgeRetrievalResponse(
                true,
                outputChunks.stream().map(KnowledgeRetrievalCapabilityProvider::toCitation).toList(),
                outputChunks.stream().map(KnowledgeRetrievalCapabilityProvider::toEvidence).toList(),
                null
        );
    }

    private static KnowledgeRetrievalCitation toCitation(RetrievedKnowledgeChunk chunk) {
        return new KnowledgeRetrievalCitation(
                chunk.knowledgeBaseId(),
                chunk.documentId(),
                chunk.chunkId(),
                chunk.titlePath(),
                chunk.content(),
                location(chunk),
                chunk.score()
        );
    }

    private static KnowledgeRetrievalEvidence toEvidence(RetrievedKnowledgeChunk chunk) {
        return new KnowledgeRetrievalEvidence(
                chunk.knowledgeBaseId(),
                chunk.documentId(),
                chunk.chunkId(),
                chunk.score(),
                chunk.signal().name(),
                chunk.indexVersion()
        );
    }

    private static String location(RetrievedKnowledgeChunk chunk) {
        if (chunk.paragraphNo() != null) {
            return "p" + chunk.paragraphNo();
        }
        if (chunk.tableCellRange() != null && !chunk.tableCellRange().isBlank()) {
            return chunk.tableCellRange();
        }
        return chunk.sourceUri();
    }

    private static RetrievalProfile resolveProfile(String profile) {
        return switch (profile.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> RetrievalProfile.STRICT;
            case "balanced" -> RetrievalProfile.BALANCED;
            case "exploratory" -> RetrievalProfile.EXPLORATORY;
            default -> throw new IllegalArgumentException("不支持的检索画像：" + profile);
        };
    }
}
