package com.aiplatform.knowledge.application;

import com.aiplatform.knowledge.application.command.SwitchKnowledgeBaseEmbeddingModelCommand;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildRequest;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeIndexRebuildTransactionService;
import com.aiplatform.knowledge.application.result.SwitchKnowledgeBaseEmbeddingModelResult;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeQueryEmbeddingPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 知识库向量模型应用服务，确保模型切换遵循“先探活、再挂载、后重索引”的生产规则。
 */
@Service
public class KnowledgeBaseEmbeddingModelApplicationService {
    private static final ErrorCode KNOWLEDGE_BASE_NOT_FOUND = ErrorCode.of("AIP-KB-NOTFOUND", "知识库不存在");
    private static final ErrorCode EMBEDDING_MODEL_DENIED = ErrorCode.of("AIP-KB-EMBEDDINGDENIED", "向量模型不可用于当前知识库");
    private static final ErrorCode EMBEDDING_MODEL_PROBE_FAILED = ErrorCode.of("AIP-KB-EMBEDDINGPROBEFAILED", "向量模型探活失败");
    private static final String PROBE_TEXT = "知识库向量模型切换探活";

    private final KnowledgeBaseRepository knowledgeBases;
    private final ModelUsagePolicyPort modelUsagePolicyPort;
    private final KnowledgeQueryEmbeddingPort queryEmbeddingPort;
    private final KnowledgeIndexRebuildTransactionService rebuildTransactions;
    private final Clock clock;

    public KnowledgeBaseEmbeddingModelApplicationService(
            KnowledgeBaseRepository knowledgeBases,
            ModelUsagePolicyPort modelUsagePolicyPort,
            KnowledgeQueryEmbeddingPort queryEmbeddingPort,
            KnowledgeIndexRebuildTransactionService rebuildTransactions,
            Clock clock
    ) {
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
        this.modelUsagePolicyPort = Objects.requireNonNull(modelUsagePolicyPort, "模型使用策略端口不能为空");
        this.queryEmbeddingPort = Objects.requireNonNull(queryEmbeddingPort, "查询向量端口不能为空");
        this.rebuildTransactions = Objects.requireNonNull(rebuildTransactions, "索引重建短事务服务不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public SwitchKnowledgeBaseEmbeddingModelResult switchEmbeddingModel(SwitchKnowledgeBaseEmbeddingModelCommand command) {
        Objects.requireNonNull(command, "切换知识库向量模型命令不能为空");
        KnowledgeBase knowledgeBase = knowledgeBases.findById(command.tenantId(), command.knowledgeBaseId())
                .orElseThrow(() -> new ApplicationException(KNOWLEDGE_BASE_NOT_FOUND, "知识库ID=" + command.knowledgeBaseId()));
        KnowledgeModelUsageDecision decision = decideEmbeddingModel(command);
        if (!decision.allowed()) {
            throw new ApplicationException(EMBEDDING_MODEL_DENIED, decision.reason());
        }
        probeEmbeddingModel(command, decision);

        Instant now = Instant.now(clock);
        KnowledgeBase switched = knowledgeBase.switchEmbeddingModel(command.embeddingModelConfigId(), now);
        var rebuild = rebuildTransactions.switchModelAndCreate(
                switched,
                new KnowledgeIndexRebuildRequest(
                        command.tenantId(),
                        command.knowledgeBaseId(),
                        command.operatorId(),
                        command.actorType(),
                        command.embeddingModelConfigId(),
                        decision.runtimeDescriptor().modelCode(),
                        ""
                )
        );
        return new SwitchKnowledgeBaseEmbeddingModelResult(
                switched.knowledgeBaseId(),
                switched.embeddingModelConfigId(),
                rebuild.scheduledJobCount()
        );
    }

    private KnowledgeModelUsageDecision decideEmbeddingModel(SwitchKnowledgeBaseEmbeddingModelCommand command) {
        return modelUsagePolicyPort.decide(new KnowledgeModelUsageRequest(
                command.tenantId(),
                command.operatorId(),
                command.actorType(),
                command.roleCodes(),
                command.permissionSnapshot(),
                command.knowledgeBaseId(),
                command.useCase(),
                command.requiredCapability(),
                command.embeddingModelConfigId(),
                Map.of("probe", true, "reason", "switch-knowledge-base-embedding-model"),
                1,
                0
        ));
    }

    private void probeEmbeddingModel(
            SwitchKnowledgeBaseEmbeddingModelCommand command,
            KnowledgeModelUsageDecision decision
    ) {
        try {
            float[] vector = queryEmbeddingPort.embedQuery(command.tenantId(), decision.runtimeDescriptor(), PROBE_TEXT);
            if (vector == null || vector.length == 0) {
                throw new IllegalStateException("向量模型探活未返回有效向量");
            }
        } catch (RuntimeException exception) {
            throw new ApplicationException(EMBEDDING_MODEL_PROBE_FAILED, safeProbeFailure(exception));
        }
    }

    private static String safeProbeFailure(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        message = message.replaceAll("sk-[A-Za-z0-9_\\-]+", "sk-***");
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
