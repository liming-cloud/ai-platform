package com.aiplatform.approval.application;

import com.aiplatform.approval.application.command.DecideApprovalTaskCommand;
import com.aiplatform.approval.application.command.ListApprovalTasksCommand;
import com.aiplatform.approval.application.error.ApprovalErrorCodes;
import com.aiplatform.approval.application.result.ApprovalDecisionResult;
import com.aiplatform.approval.application.result.ApprovalTaskPageResult;
import com.aiplatform.approval.application.result.ApprovalTaskResult;
import com.aiplatform.approval.domain.event.ApprovalDecisionRecordedEvent;
import com.aiplatform.approval.domain.model.ApprovalDecision;
import com.aiplatform.approval.domain.model.ApprovalDecisionAction;
import com.aiplatform.approval.domain.model.ApprovalDecisionRecord;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.domain.port.ApprovalDecisionEventPublisher;
import com.aiplatform.approval.domain.port.ApprovalTaskDecisionConflictException;
import com.aiplatform.approval.domain.port.ApprovalTaskRepository;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批任务应用服务，负责编排查询、决策、审批流水和决策事件发布。
 */
@Service
public class ApprovalTaskApplicationService {
    private final ApprovalTaskRepository repository;
    private final ApprovalDecisionEventPublisher eventPublisher;
    private final Clock clock;

    public ApprovalTaskApplicationService(
            ApprovalTaskRepository repository,
            ApprovalDecisionEventPublisher eventPublisher,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "审批任务仓储不能为空");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "审批决策事件发布端口不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApprovalTaskPageResult listTasks(ListApprovalTasksCommand command) {
        Objects.requireNonNull(command, "审批任务列表查询命令不能为空");
        Optional<ApprovalStatus> status = command.status().map(ApprovalTaskApplicationService::parseStatus);
        Optional<ApprovalRiskLevel> riskLevel = command.riskLevel()
                .map(ApprovalTaskApplicationService::parseRiskLevel);
        String sourceContext = command.sourceContext()
                .map(ApprovalTaskApplicationService::parseSourceContext)
                .orElse(null);
        ApprovalTaskPage page = repository.findTasks(new ApprovalTaskQuery(
                command.tenantId(),
                status.orElse(null),
                riskLevel.orElse(null),
                sourceContext,
                command.keyword(),
                command.page(),
                command.pageSize()
        ));
        return ApprovalTaskPageResult.from(page);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApprovalTaskResult getTask(TenantId tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return repository.findById(tenantId, taskId)
                .map(ApprovalTaskResult::from)
                .orElseThrow(() -> new ApplicationException(
                        ApprovalErrorCodes.APPROVAL_TASK_NOT_FOUND,
                        "审批任务 ID=" + taskId
                ));
    }

    @Transactional
    public ApprovalDecisionResult decide(DecideApprovalTaskCommand command) {
        Objects.requireNonNull(command, "审批任务决策命令不能为空");
        ApprovalTask task = repository.findById(command.tenantId(), command.taskId())
                .orElseThrow(() -> new ApplicationException(
                        ApprovalErrorCodes.APPROVAL_TASK_NOT_FOUND,
                        "审批任务 ID=" + command.taskId()
                ));
        requireExpectedEvidence(task, command.expectedEvidenceDigest());
        Instant decidedAt = Instant.now(clock);
        ApprovalDecision decision = createDecision(command, decidedAt);
        ApprovalTask decidedTask = decideTask(task, decision);
        try {
            repository.saveDecision(decidedTask);
        } catch (ApprovalTaskDecisionConflictException exception) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_DECISION_CONFLICT,
                    exception.getMessage()
            );
        }
        repository.recordDecision(new ApprovalDecisionRecord(
                command.tenantId(),
                command.taskId(),
                command.actorId(),
                decision.action(),
                decision.comment(),
                decidedAt
        ));
        eventPublisher.publish(new ApprovalDecisionRecordedEvent(
                task.tenantId(),
                task.taskId(),
                task.approvalRequestId(),
                task.sourceContext(),
                task.sourceResourceType(),
                task.sourceResourceId(),
                decidedTask.status().name(),
                decision.action().name(),
                decision.actorId(),
                decision.comment(),
                task.traceId(),
                task.evidence().schema(),
                task.evidence().digest(),
                task.targetDigest(),
                decidedAt
        ));
        return ApprovalDecisionResult.from(decidedTask);
    }

    private static void requireExpectedEvidence(ApprovalTask task, String expectedEvidenceDigest) {
        if (!task.hasActionableEvidence() || !MessageDigest.isEqual(
                task.evidence().digest().getBytes(StandardCharsets.US_ASCII),
                expectedEvidenceDigest.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_DECISION_INVALID,
                    "审批证据已变化，请刷新任务并重新核对"
            );
        }
    }

    private ApprovalTask decideTask(ApprovalTask task, ApprovalDecision decision) {
        try {
            return task.decide(decision);
        } catch (IllegalStateException exception) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_DECISION_INVALID,
                    exception.getMessage()
            );
        }
    }

    private static ApprovalStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID,
                    "审批任务状态不能为空"
            );
        }
        try {
            return ApprovalStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID,
                    "审批任务状态不支持：" + value
            );
        }
    }

    private static ApprovalRiskLevel parseRiskLevel(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID,
                    "审批任务风险等级不能为空"
            );
        }
        try {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return ApprovalRiskLevel.valueOf("HIGH".equals(normalized) ? "HIGH_RISK" : normalized);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID,
                    "审批任务风险等级不支持：" + value
            );
        }
    }

    private static String parseSourceContext(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID,
                    "审批任务来源上下文不能为空"
            );
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static ApprovalDecision createDecision(DecideApprovalTaskCommand command, Instant decidedAt) {
        try {
            return new ApprovalDecision(
                    command.actorId(),
                    ApprovalDecisionAction.valueOf(command.action().trim().toUpperCase(Locale.ROOT)),
                    command.comment(),
                    decidedAt
            );
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    ApprovalErrorCodes.APPROVAL_TASK_DECISION_INVALID,
                    exception.getMessage()
            );
        }
    }
}
