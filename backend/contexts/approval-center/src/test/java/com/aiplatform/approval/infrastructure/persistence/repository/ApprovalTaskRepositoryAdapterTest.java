package com.aiplatform.approval.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.evidence;
import static com.aiplatform.approval.support.ApprovalEvidenceTestFixtures.targetDigest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.approval.domain.model.ApprovalRequest;
import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalDecisionAction;
import com.aiplatform.approval.domain.model.ApprovalDecisionRecord;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.domain.port.ApprovalTaskDecisionConflictException;
import com.aiplatform.approval.infrastructure.persistence.converter.ApprovalTaskPersistenceConverter;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprRecordPersistenceEntity;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprTaskPersistenceEntity;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprRecordMapper;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprTaskMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalTaskRepositoryAdapterTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-27T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void requestApprovalReusesExistingPendingTask() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        when(mapper.upsertPending(any(ApprTaskPersistenceEntity.class))).thenReturn(7000L);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        String taskId = repository.requestApproval(request("3001"));

        assertThat(taskId).isEqualTo("7000");
        verify(mapper).upsertPending(any(ApprTaskPersistenceEntity.class));
    }

    @Test
    void requestApprovalCreatesTaskWhenNoPendingTaskExists() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        when(mapper.upsertPending(any(ApprTaskPersistenceEntity.class))).thenReturn(7001L);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        String taskId = repository.requestApproval(request("3001"));

        assertThat(taskId).isEqualTo("7001");
        verify(mapper).upsertPending(any(ApprTaskPersistenceEntity.class));
    }

    @Test
    void requestApprovalFailsClosedWhenInsertedTaskIdMissing() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        when(mapper.upsertPending(any(ApprTaskPersistenceEntity.class))).thenReturn(null);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        assertThatThrownBy(() -> repository.requestApproval(request("3001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审批请求幂等冲突：requestId=REQ-3001");
    }

    @Test
    void cancellationIsPersistedBeforeItReconcilesWithTheTask() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        ApprovalCancellationRequest cancellation = cancellation();
        when(mapper.upsertCancellation(cancellation)).thenReturn(8001L);
        when(mapper.reconcileCancellation(2001L, "REQ-3001")).thenReturn(1);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        repository.cancelApproval(cancellation);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).lockApprovalRequest(2001L, "REQ-3001");
        order.verify(mapper).upsertCancellation(cancellation);
        order.verify(mapper).reconcileCancellation(2001L, "REQ-3001");
        order.verify(mapper).markCancellationAfterTerminalDecision(2001L, "REQ-3001");
    }

    @Test
    void cancellationFailsClosedWhenSameRequestIdCarriesDifferentFacts() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        ApprovalCancellationRequest cancellation = cancellation();
        when(mapper.upsertCancellation(cancellation)).thenReturn(null);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        assertThatThrownBy(() -> repository.cancelApproval(cancellation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审批撤销幂等冲突：requestId=REQ-3001");

        verify(mapper, never()).reconcileCancellation(anyLong(), any(String.class));
    }

    @Test
    void cancellationFailsBeforePersistenceWhenCoordinationLockCannotBeConfirmed() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        ApprovalCancellationRequest cancellation = cancellation();
        ApprovalTaskRepositoryAdapter repository = repository(mapper);
        when(mapper.lockApprovalRequest(2001L, "REQ-3001")).thenReturn(null);

        assertThatThrownBy(() -> repository.cancelApproval(cancellation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审批请求协调锁获取失败：requestId=REQ-3001");

        verify(mapper, never()).upsertCancellation(any(ApprovalCancellationRequest.class));
    }

    @Test
    void approvalRequestArrivalReconcilesAnEarlierCancellation() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        when(mapper.upsertPending(any(ApprTaskPersistenceEntity.class))).thenReturn(7001L);
        when(mapper.reconcileCancellation(2001L, "REQ-3001")).thenReturn(1);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        assertThat(repository.requestApproval(request("3001"))).isEqualTo("7001");

        verify(mapper).reconcileCancellation(2001L, "REQ-3001");
        verify(mapper).markCancellationAfterTerminalDecision(2001L, "REQ-3001");
    }

    @Test
    void saveUpdatesOnlyTenantScopedActiveTask() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        when(mapper.decidePending(2001L, 7001L, 0, "APPROVED", 1002L,
                Instant.parse("2026-05-27T08:05:00Z"))).thenReturn(1);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        repository.saveDecision(task());

        verify(mapper).decidePending(2001L, 7001L, 0, "APPROVED", 1002L,
                Instant.parse("2026-05-27T08:05:00Z"));
        verify(mapper, never()).updateById(any(ApprTaskPersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenTaskUpdateIsLost() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        when(mapper.decidePending(anyLong(), anyLong(), anyInt(),
                any(String.class), anyLong(), any(Instant.class))).thenReturn(0);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        assertThatThrownBy(() -> repository.saveDecision(task()))
                .isInstanceOf(ApprovalTaskDecisionConflictException.class)
                .hasMessage("审批任务已被其他审批人处理，请刷新后重试");
    }

    @Test
    void decisionRepositoryCannotMasqueradeCancellationAsManualDecision() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);
        ApprovalTask cancelled = new ApprovalTask(
                TenantId.of("2001"),
                "7001",
                "REQ-3001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                "1002",
                ApprovalStatus.CANCELLED,
                null,
                Instant.parse("2026-05-27T08:05:00Z"),
                "TRACE-EVENT-001",
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:05:00Z"),
                1
        );

        assertThatThrownBy(() -> repository.saveDecision(cancelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有已完成决策的审批任务才能执行 CAS 保存");

        verify(mapper, never()).decidePending(anyLong(), anyLong(), anyInt(),
                any(String.class), anyLong(), any(Instant.class));
    }

    @Test
    void findTasksFailsClosedWhenMapperReturnsNullRows() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        ApprovalTaskQuery query = query(1, 20);
        when(mapper.countTasks(query)).thenReturn(1L);
        when(mapper.findTasks(query)).thenReturn(null);
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        assertThatThrownBy(() -> repository.findTasks(query))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审批任务查询结果不能为空");
    }

    @Test
    void oneHundredThousandMatchingRowsStillReturnOnlyTheRequestedDatabasePage() {
        ApprTaskMapper mapper = mock(ApprTaskMapper.class);
        ApprovalTaskQuery query = query(5000, 20);
        when(mapper.countTasks(query)).thenReturn(100_000L);
        when(mapper.findTasks(query)).thenReturn(List.of(existingEntity(7000L, "PENDING")));
        ApprovalTaskRepositoryAdapter repository = repository(mapper);

        ApprovalTaskPage result = repository.findTasks(query);

        assertThat(result.total()).isEqualTo(100_000L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.pageSize()).isEqualTo(20);
        verify(mapper).findTasks(query);
        verify(mapper, never()).selectList(anyQuery());
    }

    @Test
    void recordDecisionFailsClosedWhenInsertAffectsNoRows() {
        ApprRecordMapper recordMapper = mock(ApprRecordMapper.class);
        when(recordMapper.insert(any(ApprRecordPersistenceEntity.class))).thenReturn(0);
        ApprovalTaskRepositoryAdapter repository = new ApprovalTaskRepositoryAdapter(
                mock(ApprTaskMapper.class),
                recordMapper,
                new ApprovalTaskPersistenceConverter(),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> repository.recordDecision(new ApprovalDecisionRecord(
                TenantId.of("2001"),
                "7001",
                "1002",
                ApprovalDecisionAction.APPROVE,
                null,
                Instant.parse("2026-05-27T08:05:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审批流水保存失败：数据库未写入记录");
    }

    @Test
    void requestApprovalRejectsNonNumericSourceResourceIdWithChineseMessage() {
        ApprovalTaskRepositoryAdapter repository = repository(mock(ApprTaskMapper.class));

        assertThatThrownBy(() -> repository.requestApproval(request("provider-openai")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("来源资源 ID 必须是数字型数据库主键");
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<ApprTaskPersistenceEntity> anyQuery() {
        return any(LambdaQueryWrapper.class);
    }

    private static ApprovalTaskRepositoryAdapter repository(ApprTaskMapper mapper) {
        when(mapper.lockApprovalRequest(anyLong(), any(String.class))).thenReturn(1L);
        return new ApprovalTaskRepositoryAdapter(
                mapper,
                mock(ApprRecordMapper.class),
                new ApprovalTaskPersistenceConverter(),
                FIXED_CLOCK
        );
    }

    private static ApprovalRequest request(String sourceResourceId) {
        return new ApprovalRequest(
                TenantId.of("2001"),
                "REQ-3001",
                "model_config_change",
                "model-hub",
                "model_provider",
                sourceResourceId,
                "创建模型供应商 OpenAI",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                "TRACE-EVENT-001",
                evidence(sourceResourceId),
                targetDigest(sourceResourceId)
        );
    }

    private static ApprovalCancellationRequest cancellation() {
        return new ApprovalCancellationRequest(
                TenantId.of("2001"),
                "ModelApprovalCancellationRequested:2001:model_provider:REQ-3001",
                "REQ-3001",
                "model-hub",
                "model_provider",
                "3001",
                "1002",
                "供应商地址录入错误",
                "TRACE-CANCEL-001",
                Instant.parse("2026-05-27T08:00:00Z")
        );
    }

    private static ApprovalTask task() {
        return new ApprovalTask(
                TenantId.of("2001"),
                "7001",
                "REQ-3001",
                "model_config_change",
                "model-hub",
                "model_provider",
                "3001",
                "创建模型供应商 OpenAI",
                ApprovalRiskLevel.HIGH_RISK,
                "1001",
                "1002",
                ApprovalStatus.APPROVED,
                null,
                Instant.parse("2026-05-27T08:05:00Z"),
                "TRACE-EVENT-001",
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:05:00Z"),
                1
        );
    }

    private static ApprovalTaskQuery query(int page, int pageSize) {
        return new ApprovalTaskQuery(
                TenantId.of("2001"),
                ApprovalStatus.PENDING,
                ApprovalRiskLevel.HIGH_RISK,
                "model-hub",
                "供应商",
                page,
                pageSize
        );
    }

    private static ApprTaskPersistenceEntity existingEntity(Long taskId, String status) {
        ApprTaskPersistenceEntity entity = new ApprTaskPersistenceEntity();
        entity.setId(taskId);
        entity.setTenantId(2001L);
        entity.setApprovalRequestId("REQ-3001");
        entity.setApprovalType("model_config_change");
        entity.setSourceContext("model-hub");
        entity.setSourceResourceType("model_provider");
        entity.setSourceResourceId(3001L);
        entity.setTitle("创建模型供应商 OpenAI");
        entity.setRiskLevel("HIGH_RISK");
        entity.setRequesterId(1001L);
        entity.setStatus(status);
        entity.setTraceId("TRACE-EVENT-001");
        entity.setCreatedAt(Instant.parse("2026-05-27T08:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-05-27T08:00:00Z"));
        entity.setVersion(0);
        return entity;
    }
}
