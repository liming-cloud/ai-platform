package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseConflictException;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation;
import com.aiplatform.appstudio.infrastructure.persistence.converter.AppApplicationReleasePersistenceConverter;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationReleasePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppApplicationReleaseMapper;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.ApplicationReleaseOutboxMapper;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AppApplicationReleaseRepositoryAdapter implements ApplicationReleaseRepository {
    private static final String EVENT_SCHEMA_VERSION = "v1";
    private static final String EVENT_PRODUCER = "app-studio";

    private final AppApplicationReleaseMapper mapper;
    private final ApplicationReleaseOutboxMapper outbox;
    private final AppApplicationReleasePersistenceConverter converter;
    private final JsonMapper jsonMapper;

    public AppApplicationReleaseRepositoryAdapter(
            AppApplicationReleaseMapper mapper,
            ApplicationReleaseOutboxMapper outbox,
            AppApplicationReleasePersistenceConverter converter,
            JsonMapper jsonMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "应用发布版本 Mapper 不能为空");
        this.outbox = Objects.requireNonNull(outbox, "应用发布 outbox Mapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "应用发布版本转换器不能为空");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON Mapper 不能为空");
    }

    @Override
    public Optional<AiApplicationRelease> findActive(TenantId tenantId, String applicationId) {
        return Optional.ofNullable(mapper.findActive(tenantId(tenantId), applicationId(applicationId)))
                .map(converter::toDomain);
    }

    @Override
    public Map<String, AiApplicationRelease> findActiveByApplicationIds(
            TenantId tenantId,
            List<String> applicationIds
    ) {
        Objects.requireNonNull(applicationIds, "应用 ID 列表不能为空");
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        long normalizedTenantId = tenantId(tenantId);
        List<Long> normalizedApplicationIds = applicationIds.stream()
                .map(AppApplicationReleaseRepositoryAdapter::applicationId)
                .distinct()
                .toList();
        List<AppApplicationReleasePersistenceEntity> rows = mapper.findActiveByApplicationIds(
                normalizedTenantId,
                normalizedApplicationIds
        );
        if (rows == null) {
            throw new IllegalStateException("应用活动发布版本目录查询结果不能为空");
        }
        Map<String, AiApplicationRelease> result = new LinkedHashMap<>();
        for (AppApplicationReleasePersistenceEntity row : rows) {
            AiApplicationRelease release = converter.toDomain(row);
            if (!release.tenantId().equals(tenantId)
                    || !applicationIds.contains(release.applicationId())) {
                throw new IllegalStateException("应用活动发布版本目录返回越界数据");
            }
            if (result.putIfAbsent(release.applicationId(), release) != null) {
                throw new IllegalStateException("应用活动发布版本目录返回重复数据");
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public Optional<AiApplicationRelease> findByRequestId(
            TenantId tenantId,
            String applicationId,
            String requestId
    ) {
        return Optional.ofNullable(mapper.findByRequestId(
                tenantId(tenantId),
                applicationId(applicationId),
                requireText(requestId, "发布请求 ID")
        )).map(converter::toDomain);
    }

    @Override
    public Optional<AiApplicationRelease> findById(
            TenantId tenantId,
            String applicationId,
            String releaseId
    ) {
        return Optional.ofNullable(mapper.findById(
                tenantId(tenantId),
                applicationId(applicationId),
                parseRequiredLong(releaseId, "发布版本 ID")
        )).map(converter::toDomain);
    }

    @Override
    public List<AiApplicationRelease> findAll(TenantId tenantId, String applicationId) {
        List<AppApplicationReleasePersistenceEntity> rows = mapper.findAll(
                tenantId(tenantId),
                applicationId(applicationId)
        );
        if (rows == null) {
            throw new IllegalStateException("应用发布版本查询结果不能为空");
        }
        return rows.stream().map(converter::toDomain).toList();
    }

    @Override
    @Transactional
    public MutationResult createAndActivate(MutationCommand command) {
        Objects.requireNonNull(command, "应用发布版本变更命令不能为空");
        long tenantId = tenantId(command.tenantId());
        long applicationId = applicationId(command.applicationId());
        Instant lockedUpdatedAt = lockApplication(command.tenantId(), command.applicationId());

        AppApplicationReleasePersistenceEntity duplicate = mapper.findByRequestId(
                tenantId,
                applicationId,
                requireText(command.requestId(), "发布请求 ID")
        );
        if (duplicate != null) {
            AiApplicationRelease canonical = converter.toDomain(duplicate);
            if (canonical.operation() != command.operation()
                    || !Objects.equals(canonical.sourceReleaseId(), command.sourceReleaseId())) {
                throw new ApplicationReleaseConflictException("幂等请求 ID 已用于不同的发布操作");
            }
            return new MutationResult(canonical, false);
        }
        if (!lockedUpdatedAt.equals(Objects.requireNonNull(
                command.expectedApplicationUpdatedAt(),
                "预期应用更新时间不能为空"
        ))) {
            throw new ApplicationReleaseConflictException("应用草稿已变化，请重新执行发布预检");
        }

        Long releaseId = mapper.nextReleaseId();
        Long version = mapper.nextVersion(tenantId, applicationId);
        if (releaseId == null || releaseId < 1 || version == null || version < 1) {
            throw new IllegalStateException("应用发布版本号生成失败");
        }
        AppApplicationReleasePersistenceEntity entity = converter.fromCommand(releaseId, version, command);
        requireAffectedOne(mapper.insertRelease(entity), "应用发布版本写入失败");
        requireAffectedOne(mapper.activateRelease(
                tenantId,
                applicationId,
                releaseId,
                parseRequiredLong(command.actorId(), "发布人 ID"),
                command.occurredAt()
        ), "应用 active release 切换失败");
        requireAffectedOne(mapper.markApplicationPublished(
                tenantId,
                applicationId,
                parseRequiredLong(command.actorId(), "发布人 ID"),
                command.occurredAt()
        ), "应用发布状态更新失败");

        AppApplicationReleasePersistenceEntity persisted = mapper.findById(
                tenantId,
                applicationId,
                releaseId
        );
        if (persisted == null) {
            throw new IllegalStateException("应用发布版本持久化后不可见");
        }
        AiApplicationRelease release = converter.toDomain(persisted);
        requireAffectedOne(outbox.enqueue(toEvent(command, release)), "应用发布 outbox 写入失败");
        return new MutationResult(release, true);
    }

    private Instant lockApplication(TenantId tenantId, String applicationId) {
        Instant lockedUpdatedAt = mapper.lockApplication(tenantId(tenantId), applicationId(applicationId));
        if (lockedUpdatedAt == null) {
            throw new ApplicationReleaseConflictException("AI 应用不存在或已删除");
        }
        return lockedUpdatedAt;
    }

    private DomainEventMessage toEvent(MutationCommand command, AiApplicationRelease release) {
        String eventType = command.operation() == ApplicationReleaseOperation.PUBLISH
                ? "ApplicationReleasePublished"
                : "ApplicationReleaseRolledBack";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", release.applicationId());
        payload.put("releaseId", release.releaseId());
        payload.put("version", release.version());
        payload.put("operation", release.operation().name());
        payload.put("sourceReleaseId", release.sourceReleaseId());
        payload.put("requestId", release.requestId());
        payload.put("publishedBy", release.publishedBy());
        payload.put("reason", release.reason());
        try {
            return new DomainEventMessage(
                    requireText(command.eventId(), "事件 ID"),
                    eventType,
                    release.tenantId().value(),
                    "AiApplication",
                    release.applicationId(),
                    release.version(),
                    EVENT_SCHEMA_VERSION,
                    release.publishedAt(),
                    release.traceId(),
                    EVENT_PRODUCER,
                    jsonMapper.writeValueAsString(payload)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("应用发布事件序列化失败", exception);
        }
    }

    private static long tenantId(TenantId tenantId) {
        return parseRequiredLong(Objects.requireNonNull(tenantId, "租户 ID 不能为空").value(), "租户 ID");
    }

    private static long applicationId(String applicationId) {
        return parseRequiredLong(applicationId, "应用 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireAffectedOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message + "，影响行数：" + affected);
        }
    }
}
