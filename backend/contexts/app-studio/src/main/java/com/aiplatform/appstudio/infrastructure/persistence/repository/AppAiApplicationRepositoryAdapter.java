package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseConflictException;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppBoundKnowledgeBasePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppApplicationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AppAiApplicationRepositoryAdapter implements AiApplicationRepository {
    private static final int MAX_CATALOG_QUERY_WINDOW = 201;
    private final AppApplicationMapper mapper;

    public AppAiApplicationRepositoryAdapter(AppApplicationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "应用 Mapper 不能为空");
    }

    @Override
    public String nextApplicationId() {
        Long nextId = mapper.nextApplicationId();
        if (nextId == null) {
            throw new IllegalStateException("AI 应用 ID 生成失败");
        }
        return String.valueOf(nextId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireMutationLock(TenantId tenantId, String applicationId) {
        Long normalizedTenantId = parseRequiredLong(
                Objects.requireNonNull(tenantId, "租户 ID 不能为空").value(),
                "租户 ID"
        );
        Long normalizedApplicationId = parseRequiredLong(applicationId, "应用 ID");
        if (mapper.lockApplication(normalizedTenantId, normalizedApplicationId) == null) {
            throw new ApplicationReleaseConflictException("AI 应用不存在或已删除");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AiApplication save(AiApplication application) {
        AppApplicationPersistenceEntity entity = AppApplicationPersistenceEntity.fromDomain(application);
        Long tenantId = parseRequiredLong(application.tenantId().value(), "租户 ID");
        Long applicationId = parseRequiredLong(application.applicationId(), "应用 ID");
        AppApplicationPersistenceEntity existing = mapper.selectOne(new LambdaQueryWrapper<AppApplicationPersistenceEntity>()
                .eq(AppApplicationPersistenceEntity::getTenantId, tenantId)
                .eq(AppApplicationPersistenceEntity::getId, applicationId)
                .isNull(AppApplicationPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        if (existing == null) {
            if (mapper.insert(entity) != 1) {
                throw new IllegalStateException("AI 应用保存失败");
            }
        } else {
            entity.setPublishedAt(existing.getPublishedAt());
            int updated = mapper.update(entity, new LambdaUpdateWrapper<AppApplicationPersistenceEntity>()
                    .eq(AppApplicationPersistenceEntity::getTenantId, entity.getTenantId())
                    .eq(AppApplicationPersistenceEntity::getId, entity.getId())
                    .isNull(AppApplicationPersistenceEntity::getDeletedAt));
            if (updated != 1) {
                throw new IllegalStateException("AI 应用更新失败");
            }
        }
        mapper.softDeleteBindings(tenantId, applicationId, entity.getUpdatedBy());
        for (String knowledgeBaseId : application.knowledgeBaseIds()) {
            AppBoundKnowledgeBasePersistenceEntity binding = new AppBoundKnowledgeBasePersistenceEntity();
            binding.setTenantId(tenantId);
            binding.setApplicationId(applicationId);
            binding.setKnowledgeBaseId(parseRequiredLong(knowledgeBaseId, "知识库 ID"));
            binding.setStatus("ACTIVE");
            if (mapper.insertBinding(binding) != 1) {
                throw new IllegalStateException("AI 应用知识库绑定保存失败");
            }
        }
        return hydrate(entity);
    }

    @Override
    public Optional<AiApplication> findById(TenantId tenantId, String applicationId) {
        AppApplicationPersistenceEntity entity = mapper.selectOne(new LambdaQueryWrapper<AppApplicationPersistenceEntity>()
                .eq(AppApplicationPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(AppApplicationPersistenceEntity::getId, parseRequiredLong(applicationId, "应用 ID"))
                .isNull(AppApplicationPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(this::hydrate);
    }

    @Override
    public List<AiApplication> findCatalogWindowByTenant(TenantId tenantId, int limit) {
        if (limit < 1 || limit > MAX_CATALOG_QUERY_WINDOW) {
            throw new IllegalArgumentException("应用目录查询窗口必须在 1 到 " + MAX_CATALOG_QUERY_WINDOW + " 之间");
        }
        List<AppApplicationPersistenceEntity> entities = mapper.selectList(
                new LambdaQueryWrapper<AppApplicationPersistenceEntity>()
                        .eq(AppApplicationPersistenceEntity::getTenantId,
                                parseRequiredLong(tenantId.value(), "租户 ID"))
                        .isNull(AppApplicationPersistenceEntity::getDeletedAt)
                        .orderByDesc(AppApplicationPersistenceEntity::getUpdatedAt)
                        .orderByDesc(AppApplicationPersistenceEntity::getId)
                        .last("LIMIT " + limit)
        );
        return hydrateAll(tenantId, entities);
    }

    @Override
    public List<AiApplication> findAllByTenant(TenantId tenantId) {
        return hydrateAll(tenantId, mapper.selectList(new LambdaQueryWrapper<AppApplicationPersistenceEntity>()
                .eq(AppApplicationPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .isNull(AppApplicationPersistenceEntity::getDeletedAt)));
    }

    @Override
    public List<AiApplication> findVisibleApplications(TenantId tenantId, String principalId) {
        return findAllByTenant(tenantId).stream()
                .filter(application -> application.visiblePrincipalIds().contains(principalId))
                .toList();
    }

    private AiApplication hydrate(AppApplicationPersistenceEntity entity) {
        List<String> kbIds = mapper.findActiveBindings(entity.getTenantId(), entity.getId()).stream()
                .map(AppBoundKnowledgeBasePersistenceEntity::getKnowledgeBaseId)
                .map(String::valueOf)
                .toList();
        return hydrate(entity, kbIds);
    }

    private List<AiApplication> hydrateAll(
            TenantId tenantId,
            List<AppApplicationPersistenceEntity> entities
    ) {
        if (entities == null) {
            throw new IllegalStateException("AI 应用目录查询结果不能为空");
        }
        if (entities.isEmpty()) {
            return List.of();
        }
        Long normalizedTenantId = parseRequiredLong(tenantId.value(), "租户 ID");
        List<Long> applicationIds = entities.stream()
                .map(AppApplicationPersistenceEntity::getId)
                .toList();
        Set<Long> requestedIds = new HashSet<>(applicationIds);
        List<AppBoundKnowledgeBasePersistenceEntity> bindings = mapper.findActiveBindingsByApplicationIds(
                normalizedTenantId,
                applicationIds
        );
        if (bindings == null) {
            throw new IllegalStateException("AI 应用知识库绑定目录查询结果不能为空");
        }
        Map<Long, List<String>> knowledgeBaseIdsByApplication = new LinkedHashMap<>();
        for (AppBoundKnowledgeBasePersistenceEntity binding : bindings) {
            if (!normalizedTenantId.equals(binding.getTenantId())
                    || !requestedIds.contains(binding.getApplicationId())) {
                throw new IllegalStateException("AI 应用知识库绑定目录返回越界数据");
            }
            knowledgeBaseIdsByApplication
                    .computeIfAbsent(binding.getApplicationId(), ignored -> new ArrayList<>())
                    .add(String.valueOf(binding.getKnowledgeBaseId()));
        }
        return entities.stream()
                .map(entity -> {
                    if (!normalizedTenantId.equals(entity.getTenantId())) {
                        throw new IllegalStateException("AI 应用目录返回跨租户数据");
                    }
                    return hydrate(entity, knowledgeBaseIdsByApplication.getOrDefault(entity.getId(), List.of()));
                })
                .toList();
    }

    private static AiApplication hydrate(AppApplicationPersistenceEntity entity, List<String> knowledgeBaseIds) {
        return entity.toDomain(List.copyOf(knowledgeBaseIds));
    }
}
