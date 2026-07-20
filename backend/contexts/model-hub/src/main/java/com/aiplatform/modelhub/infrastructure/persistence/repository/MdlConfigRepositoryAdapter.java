package com.aiplatform.modelhub.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.modelhub.application.query.ModelConfigQueryRepository;
import com.aiplatform.modelhub.application.result.ModelConfigDetailResult;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlConfigPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlConfigMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 模型配置仓储 MyBatis-Plus 适配器，负责 options 结构化序列化和租户级查询隔离。
 */
@Repository
public class MdlConfigRepositoryAdapter implements ModelConfigRepository, ModelConfigQueryRepository {
    private final MdlConfigMapper mapper;
    private final JsonMapper objectMapper;

    public MdlConfigRepositoryAdapter(MdlConfigMapper mapper, JsonMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型配置 Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public String nextModelConfigId() {
        return requiredGeneratedId(mapper.nextModelConfigId(), "模型配置 ID");
    }

    @Override
    public ModelConfig insert(ModelConfig config) {
        requirePendingApprovalEvidence(config);
        requireInsertVersion(config.version(), "模型配置");
        MdlConfigPersistenceEntity entity = MdlConfigPersistenceEntity.fromDomain(
                config,
                writeOptions(config.options())
        );
        requiredInsertedRows(mapper.insert(entity), "模型配置");
        return entity.toDomain(readOptions(entity.getOptionsJson()));
    }

    @Override
    public Optional<ModelConfig> compareAndSet(
            ModelConfig updatedConfig,
            int expectedVersion,
            ModelConfigStatus expectedStatus
    ) {
        Objects.requireNonNull(updatedConfig, "更新后的模型配置不能为空");
        Objects.requireNonNull(expectedStatus, "预期模型配置状态不能为空");
        requireExpectedVersion(updatedConfig.version(), expectedVersion, "模型配置");
        requirePendingApprovalEvidence(updatedConfig);
        MdlConfigPersistenceEntity entity = MdlConfigPersistenceEntity.fromDomain(
                updatedConfig.withVersion(expectedVersion + 1),
                writeOptions(updatedConfig.options())
        );
        int updated = mapper.compareAndSet(entity, expectedVersion, expectedStatus.name());
        return casResult(
                updated,
                entity.toDomain(readOptions(entity.getOptionsJson())),
                "模型配置"
        );
    }

    @Override
    public Optional<ModelConfig> compareAndSetApprovalDecision(
            ModelConfig decidedConfig,
            int expectedVersion,
            ModelConfigStatus expectedStatus,
            String expectedApprovalRequestId,
            String expectedEvidenceDigest,
            String expectedTargetDigest
    ) {
        Objects.requireNonNull(decidedConfig, "审批决定后的模型配置不能为空");
        String normalizedRequestId = requireApprovalRequestId(expectedApprovalRequestId);
        String normalizedEvidenceDigest = requireDigest(expectedEvidenceDigest, "模型配置审批证据摘要");
        String normalizedTargetDigest = requireDigest(expectedTargetDigest, "模型配置审批目标摘要");
        requireExpectedVersion(decidedConfig.version(), expectedVersion, "模型配置");
        if (expectedStatus != ModelConfigStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("模型配置审批决定预期状态必须是 PENDING_APPROVAL");
        }
        if (!normalizedRequestId.equals(decidedConfig.approvalRequestId())) {
            throw new IllegalArgumentException("模型配置审批决定与预期审批请求 ID 不一致");
        }
        if (decidedConfig.status() != ModelConfigStatus.INACTIVE
                && decidedConfig.status() != ModelConfigStatus.REJECTED) {
            throw new IllegalArgumentException("模型配置审批决定必须进入 INACTIVE 或 REJECTED 状态");
        }
        int updated = mapper.compareAndSetApprovalDecision(
                approvalDecisionEntity(decidedConfig),
                expectedVersion,
                expectedStatus.name(),
                normalizedRequestId,
                normalizedEvidenceDigest,
                normalizedTargetDigest
        );
        return casResult(updated, decidedConfig.withVersion(expectedVersion + 1), "模型配置");
    }

    @Override
    public Optional<ModelConfig> findById(TenantId tenantId, String providerId, String modelConfigId) {
        MdlConfigPersistenceEntity row = mapper.selectOne(baseTenantProviderQuery(tenantId, providerId)
                .eq(MdlConfigPersistenceEntity::getId, parseRequiredLong(modelConfigId, "模型配置 ID"))
                .last("limit 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public Optional<ModelConfig> findByModelConfigId(TenantId tenantId, String modelConfigId) {
        MdlConfigPersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<MdlConfigPersistenceEntity>()
                .eq(MdlConfigPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(MdlConfigPersistenceEntity::getId, parseRequiredLong(modelConfigId, "模型配置 ID"))
                .isNull(MdlConfigPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public Optional<ModelConfig> findByProviderAndCode(TenantId tenantId, String providerId, String modelCode) {
        MdlConfigPersistenceEntity row = mapper.selectOne(baseTenantProviderQuery(tenantId, providerId)
                .eq(MdlConfigPersistenceEntity::getModelCode, modelCode)
                .last("limit 1"));
        return Optional.ofNullable(row).map(this::toDomain);
    }

    @Override
    public List<ModelConfig> findByProviderId(TenantId tenantId, String providerId) {
        List<MdlConfigPersistenceEntity> rows = mapper.selectList(baseTenantProviderQuery(tenantId, providerId));
        return requiredRows(rows, "模型配置查询结果").stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ModelConfigDetailResult> findDetailsByProviderId(TenantId tenantId, String providerId) {
        List<MdlConfigPersistenceEntity> rows = mapper.selectList(baseTenantProviderQuery(tenantId, providerId));
        return requiredRows(rows, "模型配置查询结果").stream()
                .map(this::toDetailResult)
                .toList();
    }

    private LambdaQueryWrapper<MdlConfigPersistenceEntity> baseTenantProviderQuery(
            TenantId tenantId,
            String providerId
    ) {
        return new LambdaQueryWrapper<MdlConfigPersistenceEntity>()
                .eq(MdlConfigPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(MdlConfigPersistenceEntity::getProviderId, parseRequiredLong(providerId, "模型供应商 ID"))
                .isNull(MdlConfigPersistenceEntity::getDeletedAt);
    }

    private ModelConfig toDomain(MdlConfigPersistenceEntity row) {
        return row.toDomain(readOptions(row.getOptionsJson()));
    }

    private ModelConfigDetailResult toDetailResult(MdlConfigPersistenceEntity row) {
        return ModelConfigDetailResult.from(toDomain(row));
    }

    private String writeOptions(ModelRuntimeOptions options) {
        try {
            return objectMapper.writeValueAsString(options.values());
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型 options 序列化失败", exception);
        }
    }

    private ModelRuntimeOptions readOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            throw new IllegalStateException("模型 options JSON 不能为空");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = objectMapper.readValue(optionsJson, Map.class);
            return new ModelRuntimeOptions(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型 options 反序列化失败", exception);
        }
    }

    private static String requiredGeneratedId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalStateException(fieldName + "生成失败：数据库序列未返回主键");
        }
        return String.valueOf(id);
    }

    private static MdlConfigPersistenceEntity approvalDecisionEntity(ModelConfig config) {
        MdlConfigPersistenceEntity decision = new MdlConfigPersistenceEntity();
        decision.setTenantId(parseRequiredLong(config.tenantId().value(), "租户 ID"));
        decision.setId(parseRequiredLong(config.modelConfigId(), "模型配置 ID"));
        decision.setStatus(config.status().name());
        decision.setConnectivityStatus(config.connectivityStatus().name());
        decision.setConnectivityMessage(config.connectivityMessage());
        decision.setUpdatedAt(config.updatedAt());
        return decision;
    }

    private static String requireApprovalRequestId(String approvalRequestId) {
        if (approvalRequestId == null || approvalRequestId.isBlank()) {
            throw new IllegalArgumentException("审批请求 ID不能为空");
        }
        return approvalRequestId.trim();
    }

    private static void requirePendingApprovalEvidence(ModelConfig config) {
        if (config.status() == ModelConfigStatus.PENDING_APPROVAL && !config.hasApprovalEvidence()) {
            throw new IllegalArgumentException("待审批模型配置必须持久化审批证据摘要");
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
