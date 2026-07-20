package com.aiplatform.approval.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalDecisionRecord;
import com.aiplatform.approval.domain.model.ApprovalRequest;
import com.aiplatform.approval.domain.model.ApprovalStatus;
import com.aiplatform.approval.domain.model.ApprovalTask;
import com.aiplatform.approval.domain.model.ApprovalTaskPage;
import com.aiplatform.approval.domain.model.ApprovalTaskQuery;
import com.aiplatform.approval.domain.port.ApprovalPort;
import com.aiplatform.approval.domain.port.ApprovalTaskDecisionConflictException;
import com.aiplatform.approval.domain.port.ApprovalTaskRepository;
import com.aiplatform.approval.infrastructure.persistence.converter.ApprovalTaskPersistenceConverter;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprRecordPersistenceEntity;
import com.aiplatform.approval.infrastructure.persistence.entity.ApprTaskPersistenceEntity;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprRecordMapper;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprTaskMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批任务持久化适配器；按请求身份串行化任务创建与撤销，并持久化单一终态。
 */
@Component
public class ApprovalTaskRepositoryAdapter implements ApprovalPort, ApprovalTaskRepository {
    private final ApprTaskMapper mapper;
    private final ApprRecordMapper recordMapper;
    private final ApprovalTaskPersistenceConverter converter;
    private final Clock clock;

    public ApprovalTaskRepositoryAdapter(
            ApprTaskMapper mapper,
            ApprRecordMapper recordMapper,
            ApprovalTaskPersistenceConverter converter,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "审批任务 Mapper 不能为空");
        this.recordMapper = Objects.requireNonNull(recordMapper, "审批流水 Mapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "审批任务转换器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Override
    @Transactional
    public String requestApproval(ApprovalRequest request) {
        ApprTaskPersistenceEntity entity = ApprTaskPersistenceEntity.fromRequest(request, clock);
        try {
            lockApprovalRequest(request.tenantId(), request.approvalRequestId());
            Long taskId = mapper.upsertPending(entity);
            if (taskId == null) {
                throw approvalRequestConflict(request.approvalRequestId());
            }
            reconcileCancellation(request.tenantId(), request.approvalRequestId());
            return String.valueOf(taskId);
        } catch (DataIntegrityViolationException exception) {
            throw approvalRequestConflict(request.approvalRequestId(), exception);
        }
    }

    @Override
    @Transactional
    public void cancelApproval(ApprovalCancellationRequest cancellation) {
        Objects.requireNonNull(cancellation, "审批撤销请求不能为空");
        try {
            lockApprovalRequest(cancellation.tenantId(), cancellation.approvalRequestId());
            Long cancellationId = mapper.upsertCancellation(cancellation);
            if (cancellationId == null) {
                throw cancellationConflict(cancellation.approvalRequestId());
            }
            reconcileCancellation(cancellation.tenantId(), cancellation.approvalRequestId());
        } catch (DataIntegrityViolationException exception) {
            throw cancellationConflict(cancellation.approvalRequestId(), exception);
        }
    }

    @Override
    public Optional<ApprovalTask> findById(TenantId tenantId, String taskId) {
        ApprTaskPersistenceEntity row = mapper.selectOne(baseTenantQuery(tenantId)
                .eq(ApprTaskPersistenceEntity::getId, parseRequiredLong(taskId, "审批任务 ID"))
                .last("limit 1"));
        return Optional.ofNullable(row).map(converter::toDomain);
    }

    @Override
    public ApprovalTaskPage findTasks(ApprovalTaskQuery query) {
        Objects.requireNonNull(query, "审批任务查询不能为空");
        long total = mapper.countTasks(query);
        if (total < 0) {
            throw new IllegalStateException("审批任务总数不能小于 0");
        }
        List<ApprTaskPersistenceEntity> rows = total == 0
                ? List.of()
                : requiredRows(mapper.findTasks(query), "审批任务查询结果");
        if (rows.size() > query.pageSize()) {
            throw new IllegalStateException("审批任务查询结果超过每页条数");
        }
        List<ApprovalTask> tasks = rows
                .stream()
                .map(converter::toDomain)
                .toList();
        if (tasks.stream().anyMatch(task -> !task.tenantId().equals(query.tenantId()))) {
            throw new IllegalStateException("审批任务查询返回了其他租户数据");
        }
        return new ApprovalTaskPage(tasks, total, query.page(), query.pageSize());
    }

    @Override
    public ApprovalTask saveDecision(ApprovalTask task) {
        Objects.requireNonNull(task, "审批任务不能为空");
        if ((task.status() != ApprovalStatus.APPROVED && task.status() != ApprovalStatus.REJECTED)
                || task.approverId() == null
                || task.decisionAt() == null
                || task.version() < 1) {
            throw new IllegalArgumentException("只有已完成决策的审批任务才能执行 CAS 保存");
        }
        int updated = mapper.decidePending(
                parseRequiredLong(task.tenantId().value(), "租户 ID"),
                parseRequiredLong(task.taskId(), "审批任务 ID"),
                task.version() - 1,
                task.status().name(),
                parseRequiredLong(task.approverId(), "审批人 ID"),
                task.decisionAt()
        );
        if (updated != 1) {
            throw new ApprovalTaskDecisionConflictException();
        }
        return task;
    }

    @Override
    public void recordDecision(ApprovalDecisionRecord record) {
        requiredInsertedRows(recordMapper.insert(ApprRecordPersistenceEntity.fromDomain(record)), "审批流水");
    }

    private LambdaQueryWrapper<ApprTaskPersistenceEntity> baseTenantQuery(TenantId tenantId) {
        return new LambdaQueryWrapper<ApprTaskPersistenceEntity>()
                .eq(ApprTaskPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .isNull(ApprTaskPersistenceEntity::getDeletedAt);
    }

    private void reconcileCancellation(TenantId tenantId, String approvalRequestId) {
        long databaseTenantId = parseRequiredLong(tenantId.value(), "租户 ID");
        int applied = mapper.reconcileCancellation(databaseTenantId, approvalRequestId);
        if (applied < 0 || applied > 1) {
            throw new IllegalStateException("审批撤销 CAS 返回了非法影响行数：" + applied);
        }
        int terminal = mapper.markCancellationAfterTerminalDecision(databaseTenantId, approvalRequestId);
        if (terminal < 0 || terminal > 1 || (applied == 1 && terminal != 0)) {
            throw new IllegalStateException("审批撤销终态对账返回了非法影响行数：" + terminal);
        }
    }

    private void lockApprovalRequest(TenantId tenantId, String approvalRequestId) {
        long databaseTenantId = parseRequiredLong(tenantId.value(), "租户 ID");
        Long locked = mapper.lockApprovalRequest(databaseTenantId, approvalRequestId);
        if (!Long.valueOf(1L).equals(locked)) {
            throw new IllegalStateException("审批请求协调锁获取失败：requestId=" + approvalRequestId);
        }
    }

    private static IllegalStateException approvalRequestConflict(String approvalRequestId) {
        return new IllegalStateException("审批请求幂等冲突：requestId=" + approvalRequestId);
    }

    private static IllegalStateException approvalRequestConflict(
            String approvalRequestId,
            DataIntegrityViolationException cause
    ) {
        return new IllegalStateException("审批请求与现有待审批任务冲突：requestId=" + approvalRequestId, cause);
    }

    private static IllegalStateException cancellationConflict(String approvalRequestId) {
        return new IllegalStateException("审批撤销幂等冲突：requestId=" + approvalRequestId);
    }

    private static IllegalStateException cancellationConflict(
            String approvalRequestId,
            DataIntegrityViolationException cause
    ) {
        return new IllegalStateException("审批撤销与现有事实冲突：requestId=" + approvalRequestId, cause);
    }

    private static <T> List<T> requiredRows(List<T> rows, String fieldName) {
        if (rows == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return rows;
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }
}
