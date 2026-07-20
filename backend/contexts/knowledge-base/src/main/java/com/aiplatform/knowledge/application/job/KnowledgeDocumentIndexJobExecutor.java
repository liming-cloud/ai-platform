package com.aiplatform.knowledge.application.job;

import com.aiplatform.knowledge.application.indexing.model.ChunkEmbedding;
import com.aiplatform.knowledge.application.indexing.model.PreparedKnowledgeDocumentIndex;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeDocumentEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 候选索引执行器。授权、策略决策和 embedding 均发生在数据库事务之外。
 */
@Service
public class KnowledgeDocumentIndexJobExecutor {
    private static final String MODEL_DENIED = "AIP-KB-EMBEDDING-DENIED";
    private static final String TARGET_MISMATCH = "AIP-KB-EMBEDDING-TARGET-MISMATCH";

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentChunkRepository chunks;
    private final ModelUsagePolicyPort modelUsagePolicy;
    private final RbacAuthorizationQueryPort authorization;
    private final KnowledgeDocumentEmbeddingPort embeddings;
    private final KnowledgeDocumentJobTransactionService transactions;

    public KnowledgeDocumentIndexJobExecutor(
            KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks,
            ModelUsagePolicyPort modelUsagePolicy,
            RbacAuthorizationQueryPort authorization,
            KnowledgeDocumentEmbeddingPort embeddings,
            KnowledgeDocumentJobTransactionService transactions
    ) {
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.chunks = Objects.requireNonNull(chunks, "知识切片仓储不能为空");
        this.modelUsagePolicy = Objects.requireNonNull(modelUsagePolicy, "模型策略端口不能为空");
        this.authorization = Objects.requireNonNull(authorization, "授权查询端口不能为空");
        this.embeddings = Objects.requireNonNull(embeddings, "文档向量化端口不能为空");
        this.transactions = Objects.requireNonNull(transactions, "文档作业短事务服务不能为空");
    }

    public void execute(ClaimedDocumentIngestJob claimed) {
        Objects.requireNonNull(claimed, "已认领索引任务不能为空");
        if (!DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX.equals(claimed.job().jobType())) {
            throw KnowledgeDocumentJobFailure.terminal("AIP-KB-JOB-TYPE", "索引执行器收到非索引任务");
        }
        DocumentIndexJobTarget target = claimed.optionalIndexTarget().orElseThrow(() ->
                KnowledgeDocumentJobFailure.terminal(TARGET_MISMATCH, "索引任务缺少冻结目标"));
        KnowledgeDocument document = documents.findById(
                        claimed.job().tenantId(), claimed.job().knowledgeBaseId(), claimed.job().documentId())
                .orElseThrow(() -> KnowledgeDocumentJobFailure.terminal(
                        "AIP-KB-DOCUMENT-NOT-FOUND", "冻结索引成员已不存在"));
        List<KnowledgeDocumentChunk> documentChunks = chunks.findByDocumentId(
                claimed.job().tenantId(), claimed.job().documentId());
        if (!document.indexableWith(documentChunks)) {
            throw KnowledgeDocumentJobFailure.terminal(
                    "AIP-KB-DOCUMENT-NOT-INDEXABLE", "冻结索引成员已不可索引"
            );
        }

        KnowledgeModelUsageDecision decision = decide(claimed, target, documentChunks);
        if (!decision.allowed()) {
            throw KnowledgeDocumentJobFailure.terminal(MODEL_DENIED, safeSummary(decision.reason()));
        }
        KnowledgeModelRuntimeDescriptor runtime = decision.runtimeDescriptor();
        if (!target.modelConfigId().equals(runtime.modelConfigId())
                || !target.embeddingModel().equals(runtime.modelCode())) {
            throw KnowledgeDocumentJobFailure.terminal(
                    TARGET_MISMATCH, "策略运行时与冻结索引目标不一致"
            );
        }

        ArrayList<ChunkEmbedding> results = new ArrayList<>(documentChunks.size());
        for (KnowledgeDocumentChunk chunk : documentChunks) {
            results.add(new ChunkEmbedding(
                    chunk.chunkId(),
                    embeddings.embedChunk(claimed.job().tenantId(), runtime, chunk.content())
            ));
        }
        transactions.completeIndex(
                claimed,
                new PreparedKnowledgeDocumentIndex(document, target, List.copyOf(results))
        );
    }

    private KnowledgeModelUsageDecision decide(
            ClaimedDocumentIngestJob claimed,
            DocumentIndexJobTarget target,
            List<KnowledgeDocumentChunk> documentChunks
    ) {
        return modelUsagePolicy.decide(new KnowledgeModelUsageRequest(
                claimed.job().tenantId(),
                claimed.job().operatorId(),
                target.operatorType(),
                authorization.findRoleCodes(
                        claimed.job().tenantId(), target.operatorType(), claimed.job().operatorId()),
                authorization.findPermissionCodes(
                        claimed.job().tenantId(), target.operatorType(), claimed.job().operatorId()),
                claimed.job().knowledgeBaseId(),
                KnowledgeModelUseCase.RAG_INDEXING,
                KnowledgeModelCapability.EMBEDDING,
                target.modelConfigId(),
                Map.of(
                        "operation", "execute-durable-index-rebuild",
                        "rebuildId", target.rebuildId(),
                        "indexVersion", target.indexVersion()
                ),
                documentChunks.stream().mapToLong(chunk -> chunk.content().length()).sum(),
                0
        ));
    }

    private static String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return "模型策略拒绝候选索引任务";
        }
        String sanitized = value.replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***").trim();
        return sanitized.length() > 180 ? sanitized.substring(0, 180) : sanitized;
    }
}
