package com.aiplatform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.evidence;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.targetDigest;

import com.aiplatform.approval.application.command.DecideApprovalTaskCommand;
import com.aiplatform.approval.application.command.ListApprovalTasksCommand;
import com.aiplatform.approval.application.error.ApprovalErrorCodes;
import com.aiplatform.approval.application.result.ApprovalTaskPageResult;
import com.aiplatform.approval.application.result.ApprovalTaskResult;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.domain.port.ApprovalTaskDecisionConflictException;
import com.aiplatform.approval.domain.port.ApprovalDecisionEventPublisher;
import com.aiplatform.approval.domain.port.ApprovalTaskRepository;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalTaskApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-31T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void listTasksFiltersStatusUsingApprovalStatusEnum() {
        ApprovalTaskRepository repository = mock(ApprovalTaskRepository.class);
        ApprovalTaskApplicationService service = service(repository);
        when(repository.findTasks(any(ApprovalTaskQuery.class)))
                .thenReturn(new ApprovalTaskPage(List.of(task(ApprovalStatus.PENDING)), 100_000L, 3, 1));

        ApprovalTaskPageResult results = service.listTasks(
                new ListApprovalTasksCommand(
                        TENANT_ID,
                        Optional.of("pending"),
                        Optional.of("HIGH"),
                        Optional.of("MODEL_HUB"),
                        "供应商",
                        3,
                        1
                )
        );

        assertThat(results.items()).hasSize(1);
        assertThat(results.total()).isEqualTo(100_000L);
        assertThat(results.page()).isEqualTo(3);
        assertThat(results.pageSize()).isEqualTo(1);
        verify(repository).findTasks(new ApprovalTaskQuery(
                TENANT_ID,
                ApprovalStatus.PENDING,
                ApprovalRiskLevel.HIGH_RISK,
                "model-hub",
                "供应商",
                3,
                1
        ));
    }

    @Test
    void listTasksRejectsUnsupportedStatusInsteadOfUnexpectedException() {
        ApprovalTaskRepository repository = mock(ApprovalTaskRepository.class);
        ApprovalTaskApplicationService service = service(repository);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.listTasks(
                commandWithStatus("BROKEN")
        ));

        assertThat(exception.errorCode()).isEqualTo(ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID);
        assertThat(exception.detail()).isEqualTo("审批任务状态不支持：BROKEN");
        verify(repository, never()).findTasks(any());
    }

    @Test
    void listTasksRejectsBlankStatusInsteadOfListingAll() {
        ApprovalTaskRepository repository = mock(ApprovalTaskRepository.class);
        ApprovalTaskApplicationService service = service(repository);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.listTasks(
                commandWithStatus(" ")
        ));

        assertThat(exception.errorCode()).isEqualTo(ApprovalErrorCodes.APPROVAL_TASK_QUERY_INVALID);
        assertThat(exception.detail()).isEqualTo("审批任务状态不能为空");
        verify(repository, never()).findTasks(any());
    }

    @Test
    void getsOneTaskFromTheTenantScopedRepositoryForDecisionReconciliation() {
        ApprovalTaskRepository repository = mock(ApprovalTaskRepository.class);
        ApprovalTaskApplicationService service = service(repository);
        when(repository.findById(TENANT_ID, "8101")).thenReturn(Optional.of(task(ApprovalStatus.APPROVED)));

        ApprovalTaskResult result = service.getTask(TENANT_ID, "8101");

        assertThat(result.taskId()).isEqualTo("8101");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.evidenceDigest()).isEqualTo(evidence("3001").digest());
        verify(repository).findById(TENANT_ID, "8101");
    }

    @Test
    void decisionRejectsRequesterSelfApprovalBeforeAnyStateOrEventWrite() {
        ApprovalTaskRepository repository = mock(ApprovalTaskRepository.class);
        ApprovalDecisionEventPublisher events = mock(ApprovalDecisionEventPublisher.class);
        ApprovalTaskApplicationService service = new ApprovalTaskApplicationService(repository, events, CLOCK);
        when(repository.findById(TENANT_ID, "8101")).thenReturn(Optional.of(task(ApprovalStatus.PENDING)));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.decide(
                new DecideApprovalTaskCommand(
                        TENANT_ID,
                        "1001",
                        "8101",
                        "APPROVE",
                        "自行确认",
                        evidence("3001").digest()
                )
        ));

        assertThat(exception.errorCode()).isEqualTo(ApprovalErrorCodes.APPROVAL_TASK_DECISION_INVALID);
        assertThat(exception.detail()).isEqualTo("申请人不能审批自己的申请");
        verify(repository, never()).saveDecision(any());
        verify(repository, never()).recordDecision(any());
        verify(events, never()).publish(any());
    }

    @Test
    void decisionCasConflictReturnsStableBusinessErrorWithoutRecordOrOutbox() {
        ApprovalTaskRepository repository = mock(ApprovalTaskRepository.class);
        ApprovalDecisionEventPublisher events = mock(ApprovalDecisionEventPublisher.class);
        ApprovalTaskApplicationService service = new ApprovalTaskApplicationService(repository, events, CLOCK);
        when(repository.findById(TENANT_ID, "8101")).thenReturn(Optional.of(task(ApprovalStatus.PENDING)));
        when(repository.saveDecision(any())).thenThrow(new ApprovalTaskDecisionConflictException());

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.decide(
                new DecideApprovalTaskCommand(
                        TENANT_ID,
                        "9001",
                        "8101",
                        "APPROVE",
                        "同意上线",
                        evidence("3001").digest()
                )
        ));

        assertThat(exception.errorCode()).isEqualTo(ApprovalErrorCodes.APPROVAL_TASK_DECISION_CONFLICT);
        assertThat(exception.detail()).isEqualTo("审批任务已被其他审批人处理，请刷新后重试");
        verify(repository, never()).recordDecision(any());
        verify(events, never()).publish(any());
    }

    private static ListApprovalTasksCommand commandWithStatus(String status) {
        return new ListApprovalTasksCommand(
                TENANT_ID,
                Optional.of(status),
                Optional.empty(),
                Optional.empty(),
                "",
                1,
                20
        );
    }

    private static ApprovalTaskApplicationService service(ApprovalTaskRepository repository) {
        return new ApprovalTaskApplicationService(
                repository,
                mock(ApprovalDecisionEventPublisher.class),
                CLOCK
        );
    }

    private static ApprovalTask task(ApprovalStatus status) {
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        return new ApprovalTask(
                TENANT_ID,
                "8101",
                "REQ-8101",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                status == ApprovalStatus.PENDING ? null : "9001",
                status,
                now.plusSeconds(3600),
                status == ApprovalStatus.PENDING ? null : now,
                "TRACE-APPROVAL-001",
                evidence("3001"),
                targetDigest("3001"),
                now,
                now,
                0
        );
    }
}
