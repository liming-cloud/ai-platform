package com.aiplatform.modelhub.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.modelhub.application.query.ModelProviderQueryRepository;
import com.aiplatform.modelhub.application.result.ModelProviderDetailResult;
import com.aiplatform.modelhub.domain.ModelProviderAuthTypes;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlProviderPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlProviderMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 模型供应商仓储 MyBatis-Plus 适配器，所有查询必须携带租户与逻辑删除条件。
 */
@Repository
public class MdlProviderRepositoryAdapter implements ModelProviderRepository, ModelProviderQueryRepository {
    private final MdlProviderMapper mapper;
    private final MdlSecretMapper secretMapper;

    public MdlProviderRepositoryAdapter(MdlProviderMapper mapper, MdlSecretMapper secretMapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型供应商 Mapper 不能为空");
        this.secretMapper = Objects.requireNonNull(secretMapper, "模型供应商凭证材料 Mapper 不能为空");
    }

    @Override
    public String nextProviderId() {
        return requiredGeneratedId(mapper.nextProviderId(), "模型供应商 ID");
    }

    @Override
    public ModelProvider insert(ModelProvider provider) {
        requirePendingApprovalEvidence(provider);
        requireInsertVersion(provider.version(), "模型供应商");
        MdlProviderPersistenceEntity entity = MdlProviderPersistenceEntity.fromDomain(provider);
        requiredInsertedRows(mapper.insert(entity), "模型供应商");
        return entity.toDomain(provider.secretRef());
    }

    @Override
    public Optional<ModelProvider> compareAndSet(
            ModelProvider updatedProvider,
            int expectedVersion,
            ModelProviderStatus expectedStatus
    ) {
        Objects.requireNonNull(updatedProvider, "更新后的模型供应商不能为空");
        Objects.requireNonNull(expectedStatus, "预期模型供应商状态不能为空");
        requireExpectedVersion(updatedProvider.version(), expectedVersion, "模型供应商");
        requirePendingApprovalEvidence(updatedProvider);
        ModelProvider persisted = updatedProvider.withVersion(expectedVersion + 1);
        MdlProviderPersistenceEntity entity = MdlProviderPersistenceEntity.fromDomain(persisted);
        int updated = mapper.compareAndSet(entity, expectedVersion, expectedStatus.name());
        return casResult(updated, persisted, "模型供应商");
    }

    @Override
    public Optional<ModelProvider> compareAndSetApprovalDecision(
            ModelProvider decidedProvider,
            int expectedVersion,
            ModelProviderStatus expectedStatus,
            String expectedApprovalRequestId,
            String expectedEvidenceDigest,
            String expectedTargetDigest
    ) {
        Objects.requireNonNull(decidedProvider, "审批决定后的模型供应商不能为空");
        String normalizedRequestId = requireApprovalRequestId(expectedApprovalRequestId);
        String normalizedEvidenceDigest = requireDigest(expectedEvidenceDigest, "模型供应商审批证据摘要");
        String normalizedTargetDigest = requireDigest(expectedTargetDigest, "模型供应商审批目标摘要");
        requireExpectedVersion(decidedProvider.version(), expectedVersion, "模型供应商");
        if (expectedStatus != ModelProviderStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("模型供应商审批决定预期状态必须是 PENDING_APPROVAL");
        }
        if (!normalizedRequestId.equals(decidedProvider.approvalRequestId())) {
            throw new IllegalArgumentException("模型供应商审批决定与预期审批请求 ID 不一致");
        }
        if (decidedProvider.status() != ModelProviderStatus.ACTIVE
                && decidedProvider.status() != ModelProviderStatus.DISABLED) {
            throw new IllegalArgumentException("模型供应商审批决定必须进入 ACTIVE 或 DISABLED 状态");
        }
        int updated = mapper.compareAndSetApprovalDecision(
                approvalDecisionEntity(decidedProvider),
                expectedVersion,
                expectedStatus.name(),
                normalizedRequestId,
                normalizedEvidenceDigest,
                normalizedTargetDigest
        );
        return casResult(updated, decidedProvider.withVersion(expectedVersion + 1), "模型供应商");
    }

    @Override
    public Optional<ModelProvider> findById(TenantId tenantId, String providerId) {
        MdlProviderPersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<MdlProviderPersistenceEntity>()
                .eq(MdlProviderPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(MdlProviderPersistenceEntity::getId, parseRequiredLong(providerId, "模型供应商 ID"))
                .isNull(MdlProviderPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public List<ModelProvider> findAll(TenantId tenantId) {
        List<MdlProviderPersistenceEntity> rows = mapper.selectList(new LambdaQueryWrapper<MdlProviderPersistenceEntity>()
                .eq(MdlProviderPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .isNull(MdlProviderPersistenceEntity::getDeletedAt));
        return requiredRows(rows, "模型供应商查询结果").stream().map(this::toDomain).toList();
    }

    @Override
    public List<ModelProviderDetailResult> findAllDetails(TenantId tenantId) {
        List<MdlProviderPersistenceEntity> rows = mapper.selectList(new LambdaQueryWrapper<MdlProviderPersistenceEntity>()
                .eq(MdlProviderPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .isNull(MdlProviderPersistenceEntity::getDeletedAt));
        return requiredRows(rows, "模型供应商详情查询结果").stream()
                .map(this::toDetailResult)
                .toList();
    }

    private ModelProvider toDomain(MdlProviderPersistenceEntity row) {
        return row.toDomain(loadSecretRef(row));
    }

    private ModelProviderDetailResult toDetailResult(MdlProviderPersistenceEntity row) {
        return new ModelProviderDetailResult(
                requireProviderId(row.getId()),
                row.getProviderCode(),
                row.getProviderName(),
                row.getBaseUrl(),
                row.getStatus(),
                row.getApprovalRequestId(),
                requireVersion(row.getVersion(), "模型供应商版本")
        );
    }

    private ModelSecretRef loadSecretRef(MdlProviderPersistenceEntity row) {
        if (ModelProviderAuthTypes.isNoAuth(row.getAuthType())) {
            return null;
        }
        MdlSecretPersistenceEntity secret = secretMapper.selectOne(new LambdaQueryWrapper<MdlSecretPersistenceEntity>()
                .eq(MdlSecretPersistenceEntity::getTenantId, row.getTenantId())
                .eq(MdlSecretPersistenceEntity::getProviderId, row.getId())
                .eq(MdlSecretPersistenceEntity::getStatus, "ACTIVE")
                .isNull(MdlSecretPersistenceEntity::getDeletedAt)
                .orderByDesc(MdlSecretPersistenceEntity::getId)
                .last("limit 1"));
        if (secret == null) {
            throw new IllegalStateException("模型供应商凭证材料缺失：providerId=" + row.getId());
        }
        return new ModelSecretRef(requiredSecretId(secret.getId()), secret.getSecretMaskedValue());
    }

    private static String requiredSecretId(Long id) {
        if (id == null) {
            throw new IllegalStateException("模型供应商凭证材料损坏：数据库主键不能为空");
        }
        return String.valueOf(id);
    }

    private static String requiredGeneratedId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalStateException(fieldName + "生成失败：数据库序列未返回主键");
        }
        return String.valueOf(id);
    }

    private static String requireProviderId(Long id) {
        if (id == null) {
            throw new IllegalStateException("模型供应商详情损坏：数据库主键不能为空");
        }
        return String.valueOf(id);
    }

    private static int requireVersion(Integer version, String fieldName) {
        if (version == null || version < 0) {
            throw new IllegalStateException(fieldName + "不能为空且不能小于 0");
        }
        return version;
    }

    private static MdlProviderPersistenceEntity approvalDecisionEntity(ModelProvider provider) {
        MdlProviderPersistenceEntity decision = new MdlProviderPersistenceEntity();
        decision.setTenantId(parseRequiredLong(provider.tenantId().value(), "租户 ID"));
        decision.setId(parseRequiredLong(provider.providerId(), "模型供应商 ID"));
        decision.setStatus(provider.status().name());
        decision.setUpdatedAt(provider.updatedAt());
        return decision;
    }

    private static String requireApprovalRequestId(String approvalRequestId) {
        if (approvalRequestId == null || approvalRequestId.isBlank()) {
            throw new IllegalArgumentException("审批请求 ID不能为空");
        }
        return approvalRequestId.trim();
    }

    private static void requirePendingApprovalEvidence(ModelProvider provider) {
        if (provider.status() == ModelProviderStatus.PENDING_APPROVAL && !provider.hasApprovalEvidence()) {
            throw new IllegalArgumentException("待审批模型供应商必须持久化审批证据摘要");
        }
    }

    private static String requireDigest(String value, String fieldName) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static <T> Optional<T> casResult(int updated, T aggregate, String aggregateName) {
        if (updated == 0) {
            return Optional.empty();
        }
        if (updated == 1) {
            return Optional.of(aggregate);
        }
        throw new IllegalStateException(aggregateName + "原子写入返回异常行数：" + updated);
    }

    private static void requireInsertVersion(int version, String aggregateName) {
        if (version != 0) {
            throw new IllegalArgumentException(aggregateName + "新增版本必须是 0");
        }
    }

    private static void requireExpectedVersion(int aggregateVersion, int expectedVersion, String aggregateName) {
        if (expectedVersion < 0 || aggregateVersion != expectedVersion) {
            throw new IllegalArgumentException(aggregateName + "聚合版本与预期版本不一致");
        }
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
