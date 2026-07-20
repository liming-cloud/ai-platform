package com.aiplatform.knowledge.application.indexing.service;

import com.aiplatform.knowledge.application.command.IndexKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildRequest;
import com.aiplatform.knowledge.application.result.IndexKnowledgeDocumentResult;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * HTTP 文档索引请求只创建或复用服务端 generation；实际 embedding 由 durable worker 执行。
 */
@Service
public class KnowledgeDocumentIndexApplicationService {
    private static final String STATUS_PENDING = "PENDING";
    private static final ErrorCode DOCUMENT_NOT_FOUND = ErrorCode.of("AIP-KB-NOTFOUND", "知识文档不存在");
    private static final ErrorCode DOCUMENT_NOT_CHUNKED = ErrorCode.of("AIP-KB-NOTCHUNKED", "文档尚未完成切块");
    private static final ErrorCode MODEL_DENIED = ErrorCode.of("AIP-KB-EMBEDDINGDENIED", "索引模型策略拒绝");
    private static final ErrorCode NEXT_GENERATION_REQUIRED = ErrorCode.of(
            "AIP-KB-NEXTGENERATION", "当前 generation 已冻结，请等待下一代索引任务"
    );

    private final KnowledgeBaseRepository knowledgeBases;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentChunkRepository chunks;
    private final ModelUsagePolicyPort modelUsagePolicy;
    private final RbacAuthorizationQueryPort authorization;
    private final KnowledgeIndexRebuildTransactionService rebuildTransactions;

    public KnowledgeDocumentIndexApplicationService(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks,
            ModelUsagePolicyPort modelUsagePolicy,
            RbacAuthorizationQueryPort authorization,
            KnowledgeIndexRebuildTransactionService rebuildTransactions
    ) {
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.chunks = Objects.requireNonNull(chunks, "知识切片仓储不能为空");
        this.modelUsagePolicy = Objects.requireNonNull(modelUsagePolicy, "模型策略端口不能为空");
        this.authorization = Objects.requireNonNull(authorization, "授权查询端口不能为空");
        this.rebuildTransactions = Objects.requireNonNull(rebuildTransactions, "索引重建短事务服务不能为空");
    }

    public IndexKnowledgeDocumentResult indexDocument(IndexKnowledgeDocumentCommand command) {
        Objects.requireNonNull(command, "文档索引命令不能为空");
        KnowledgeBase knowledgeBase = knowledgeBases.findById(command.tenantId(), command.knowledgeBaseId())
                .orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "知识库ID=" + command.knowledgeBaseId()));
        KnowledgeDocument document = documents.findById(
                        command.tenantId(), command.knowledgeBaseId(), command.documentId())
                .orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + command.documentId()));
        List<KnowledgeDocumentChunk> documentChunks = chunks.findByDocumentId(
                command.tenantId(), command.documentId());
        if (!document.indexableWith(documentChunks)) {
            throw new ApplicationException(DOCUMENT_NOT_CHUNKED, "文档ID=" + command.documentId());
        }

        KnowledgeModelUsageDecision decision = decideModelUsage(command, knowledgeBase, documentChunks);
        if (!decision.allowed()) {
            throw new ApplicationException(MODEL_DENIED, safeReason(decision.reason()));
        }
        var scheduled = rebuildTransactions.createOrReuse(new KnowledgeIndexRebuildRequest(
                command.tenantId(),
                command.knowledgeBaseId(),
                command.operatorId(),
                command.operatorType(),
                knowledgeBase.embeddingModelConfigId(),
                decision.runtimeDescriptor().modelCode(),
                command.documentId()
        ));
        if (scheduled.requestedDocumentJobId().isEmpty()) {
            throw new ApplicationException(NEXT_GENERATION_REQUIRED, "文档ID=" + command.documentId());
        }
        return new IndexKnowledgeDocumentResult(
                command.documentId(),
                scheduled.requestedDocumentJobId(),
                STATUS_PENDING,
                0,
                ""
        );
    }

    private KnowledgeModelUsageDecision decideModelUsage(
            IndexKnowledgeDocumentCommand command,
            KnowledgeBase knowledgeBase,
            List<KnowledgeDocumentChunk> documentChunks
    ) {
        return modelUsagePolicy.decide(new KnowledgeModelUsageRequest(
                command.tenantId(),
                command.operatorId(),
                command.operatorType(),
                authorization.findRoleCodes(command.tenantId(), command.operatorType(), command.operatorId()),
                authorization.findPermissionCodes(command.tenantId(), command.operatorType(), command.operatorId()),
                command.knowledgeBaseId(),
                KnowledgeModelUseCase.RAG_INDEXING,
                KnowledgeModelCapability.EMBEDDING,
                knowledgeBase.embeddingModelConfigId(),
                Map.of("operation", "schedule-durable-index-rebuild"),
                estimateInputTokens(documentChunks),
                0
        ));
    }

    private static long estimateInputTokens(List<KnowledgeDocumentChunk> documentChunks) {
        return documentChunks.stream()
                .map(KnowledgeDocumentChunk::content)
                .filter(Objects::nonNull)
                .mapToLong(String::length)
                .sum();
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "模型策略未提供可公开原因";
        }
        String normalized = reason.replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***").trim();
        return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
    }
}
