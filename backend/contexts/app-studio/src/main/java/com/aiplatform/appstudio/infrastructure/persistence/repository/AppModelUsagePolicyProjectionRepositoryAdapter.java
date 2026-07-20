package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.modelpolicy.AppModelUsagePolicyProjectionRepository;
import com.aiplatform.appstudio.infrastructure.persistence.converter.AppModelUsagePolicyProjectionPersistenceConverter;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppModelUsagePolicyProjectionPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppModelUsagePolicyProjectionMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * AI 应用模型策略投影仓储适配器，使用 MyBatis-Plus 维护本地投影表。
 */
@Repository
public class AppModelUsagePolicyProjectionRepositoryAdapter implements AppModelUsagePolicyProjectionRepository {
    private final AppModelUsagePolicyProjectionMapper mapper;
    private final AppModelUsagePolicyProjectionPersistenceConverter converter;

    public AppModelUsagePolicyProjectionRepositoryAdapter(
            AppModelUsagePolicyProjectionMapper mapper,
            AppModelUsagePolicyProjectionPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "模型策略投影 Mapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "模型策略投影转换器不能为空");
    }

    @Override
    public Optional<AppModelUsagePolicyProjection> findByModelConfigId(TenantId tenantId, String modelConfigId) {
        AppModelUsagePolicyProjectionPersistenceEntity row = mapper.selectOne(
                new LambdaQueryWrapper<AppModelUsagePolicyProjectionPersistenceEntity>()
                        .eq(AppModelUsagePolicyProjectionPersistenceEntity::getTenantId,
                                parseRequiredLong(tenantId.value(), "租户 ID"))
                        .eq(AppModelUsagePolicyProjectionPersistenceEntity::getModelConfigId,
                                parseRequiredLong(modelConfigId, "模型配置 ID"))
                        .last("limit 1")
        );
        return Optional.ofNullable(row).map(converter::toDomain);
    }

    @Override
    public AppModelUsagePolicyProjection save(AppModelUsagePolicyProjection projection) {
        Long tenantId = parseRequiredLong(projection.tenantId().value(), "租户 ID");
        Long modelConfigId = parseRequiredLong(projection.modelConfigId(), "模型配置 ID");
        AppModelUsagePolicyProjectionPersistenceEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<AppModelUsagePolicyProjectionPersistenceEntity>()
                        .eq(AppModelUsagePolicyProjectionPersistenceEntity::getTenantId, tenantId)
                        .eq(AppModelUsagePolicyProjectionPersistenceEntity::getModelConfigId, modelConfigId)
                        .last("limit 1")
        );
        if (supersedes(existing, projection)) {
            return converter.toDomain(existing);
        }
        AppModelUsagePolicyProjectionPersistenceEntity entity = converter.toEntity(
                projection,
                existing == null ? null : existing.getId(),
                existing == null ? null : existing.getVersion()
        );
        if (existing == null) {
            if (mapper.insert(entity) != 1) {
                throw new IllegalStateException("AI 应用模型策略投影保存失败");
            }
        } else {
            int currentVersion = requiredVersion(existing.getVersion());
            entity.setCreatedAt(existing.getCreatedAt());
            entity.setVersion(currentVersion + 1);
            int updated = mapper.update(
                    entity,
                    new LambdaUpdateWrapper<AppModelUsagePolicyProjectionPersistenceEntity>()
                            .eq(AppModelUsagePolicyProjectionPersistenceEntity::getId, existing.getId())
                            .eq(AppModelUsagePolicyProjectionPersistenceEntity::getVersion, currentVersion)
            );
            if (updated != 1) {
                return resolveConcurrentUpdate(projection);
            }
        }
        return findByModelConfigId(projection.tenantId(), projection.modelConfigId())
                .orElseThrow(() -> new IllegalStateException("AI 应用模型策略投影回读失败"));
    }

    private AppModelUsagePolicyProjection resolveConcurrentUpdate(AppModelUsagePolicyProjection projection) {
        return findByModelConfigId(projection.tenantId(), projection.modelConfigId())
                .filter(current -> supersedes(current.policyRevision(), current.policyDigest(), projection))
                .orElseThrow(() -> new IllegalStateException("AI 应用模型策略投影更新失败"));
    }

    private static boolean supersedes(
            AppModelUsagePolicyProjectionPersistenceEntity existing,
            AppModelUsagePolicyProjection incoming
    ) {
        return existing != null && supersedes(
                requiredPolicyRevision(existing.getPolicyRevision()),
                requiredPolicyDigest(existing.getPolicyDigest()),
                incoming
        );
    }

    private static boolean supersedes(long currentRevision, String currentDigest, AppModelUsagePolicyProjection incoming) {
        if (currentRevision > incoming.policyRevision()) {
            return true;
        }
        if (currentRevision < incoming.policyRevision()) {
            return false;
        }
        if (!currentDigest.equals(incoming.policyDigest())) {
            throw new IllegalStateException("AI 应用模型策略同一修订号载荷摘要冲突");
        }
        return true;
    }

    private static long requiredPolicyRevision(Long revision) {
        if (revision == null || revision <= 0) {
            throw new IllegalStateException("AI 应用模型策略投影修订号非法");
        }
        return revision;
    }

    private static String requiredPolicyDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("AI 应用模型策略投影摘要非法");
        }
        return digest;
    }

    private static int requiredVersion(Integer version) {
        if (version == null) {
            throw new IllegalStateException("AI 应用模型策略投影乐观锁版本不能为空");
        }
        return version;
    }
}
