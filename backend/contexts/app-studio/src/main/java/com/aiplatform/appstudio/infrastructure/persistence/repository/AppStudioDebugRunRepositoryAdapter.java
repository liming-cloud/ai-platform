package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunQueryFilter;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSummary;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppChatRetrievalEvidencePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppStudioDebugRunMapper;
import com.aiplatform.appstudio.infrastructure.persistence.projection.AppStudioDebugRunDetailRow;
import com.aiplatform.appstudio.infrastructure.persistence.projection.AppStudioDebugRunSummaryRow;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * App Studio 调试运行仓储适配器：负责 evidence 快照落库和历史读模型查询。
 */
@Repository
public class AppStudioDebugRunRepositoryAdapter implements AppStudioDebugRunSnapshotPort, AppStudioDebugRunQueryPort {
    private final AppStudioDebugRunMapper mapper;

    public AppStudioDebugRunRepositoryAdapter(AppStudioDebugRunMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "调试运行 Mapper 不能为空");
    }

    @Override
    public void record(AppStudioDebugRunSnapshot snapshot) {
        snapshot.evidences().stream()
                .map(evidence -> AppChatRetrievalEvidencePersistenceEntity.fromSnapshot(snapshot, evidence))
                .forEach(mapper::insertRetrievalEvidence);
    }

    @Override
    public List<AppStudioDebugRunSummary> listDebugRuns(AppStudioDebugRunQueryFilter filter) {
        Long tenantId = parseRequiredLong(filter.tenantId().value(), "租户 ID");
        return mapper.findDebugRunSummaries(
                        tenantId,
                        parseRequiredLong(filter.applicationId(), "应用 ID"),
                        parseRequiredLong(filter.userId(), "用户 ID"),
                        parseOptionalLong(filter.sessionId()),
                        filter.traceId(),
                        parseOptionalLong(filter.modelInvocationLogId()),
                        filter.invocationStatus(),
                        filter.limit())
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<AppStudioDebugRunDetail> findDebugRunDetail(TenantId tenantId, String applicationId, String userId, String assistantMessageId) {
        Long tenantDatabaseId = parseRequiredLong(tenantId.value(), "租户 ID");
        AppStudioDebugRunDetailRow row = mapper.findDebugRunDetail(
                tenantDatabaseId,
                parseRequiredLong(applicationId, "应用 ID"),
                parseOptionalLong(userId),
                parseRequiredLong(assistantMessageId, "助手消息 ID"));
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new AppStudioDebugRunDetail(
                String.valueOf(row.getAssistantMessageId()),
                String.valueOf(row.getUserMessageId()),
                String.valueOf(row.getApplicationId()),
                String.valueOf(row.getSessionId()),
                row.getTraceId(),
                row.getModelInvocationLogId() == null ? null : String.valueOf(row.getModelInvocationLogId()),
                row.getInvocationStatus(),
                isAnswerable(row.getInvocationStatus(), row.getDecisionSource()),
                row.getUserMessageContent(),
                row.getAssistantMessageContent(),
                !isAnswerable(row.getInvocationStatus(), row.getDecisionSource())
                        ? row.getAssistantMessageContent()
                        : null,
                mapper.findCitationRecordsByChatMessageId(tenantDatabaseId, row.getAssistantMessageId()).stream()
                        .map(citation -> citation.toDomain())
                        .toList(),
                mapper.findRetrievalEvidencesByAssistantMessageId(tenantDatabaseId, row.getAssistantMessageId()).stream()
                        .map(AppChatRetrievalEvidencePersistenceEntity::toDomain)
                        .toList(),
                row.getProviderCode(),
                row.getModelCode(),
                row.getDecisionSource(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getLatencyMs(),
                row.getSourceContext(),
                row.getSourceResourceType(),
                row.getSourceResourceId(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getCreatedAt()));
    }

    private AppStudioDebugRunSummary toSummary(AppStudioDebugRunSummaryRow row) {
        return new AppStudioDebugRunSummary(
                String.valueOf(row.getAssistantMessageId()),
                String.valueOf(row.getUserMessageId()),
                String.valueOf(row.getApplicationId()),
                String.valueOf(row.getSessionId()),
                row.getTraceId(),
                row.getModelInvocationLogId() == null ? null : String.valueOf(row.getModelInvocationLogId()),
                row.getInvocationStatus(),
                isAnswerable(row.getInvocationStatus(), row.getDecisionSource()),
                zeroIfNull(row.getCitationCount()),
                zeroIfNull(row.getEvidenceCount()),
                row.getProviderCode(),
                row.getModelCode(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getLatencyMs(),
                row.getCreatedAt());
    }

    private static Long parseOptionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredLong(value, "可选数字 ID");
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean isAnswerable(String invocationStatus, String decisionSource) {
        if ("FAILED".equals(invocationStatus)) {
            return false;
        }
        return decisionSource == null || !"knowledge-retrieval-fail-closed".equals(decisionSource);
    }
}
