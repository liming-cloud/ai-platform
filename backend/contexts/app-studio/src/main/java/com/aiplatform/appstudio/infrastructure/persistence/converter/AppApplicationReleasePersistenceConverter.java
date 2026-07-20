package com.aiplatform.appstudio.infrastructure.persistence.converter;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationReleasePersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

@Component
public class AppApplicationReleasePersistenceConverter {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JsonMapper jsonMapper;

    public AppApplicationReleasePersistenceConverter(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON Mapper 不能为空");
    }

    public AppApplicationReleasePersistenceEntity fromCommand(
            long releaseId,
            long version,
            ApplicationReleaseRepository.MutationCommand command
    ) {
        AiApplication snapshot = command.snapshot();
        AppApplicationReleasePersistenceEntity entity = new AppApplicationReleasePersistenceEntity();
        entity.setId(releaseId);
        entity.setTenantId(parseRequiredLong(command.tenantId().value(), "租户 ID"));
        entity.setApplicationId(parseRequiredLong(command.applicationId(), "应用 ID"));
        entity.setVersionNo(version);
        entity.setSourceReleaseId(command.sourceReleaseId() == null
                ? null
                : parseRequiredLong(command.sourceReleaseId(), "来源发布版本 ID"));
        entity.setRequestId(command.requestId());
        entity.setOperation(command.operation().name());
        entity.setApplicationType(snapshot.type().name());
        entity.setApplicationName(snapshot.name());
        entity.setDescription(snapshot.description());
        entity.setModelConfigId(parseRequiredLong(snapshot.modelConfigId(), "模型配置 ID"));
        entity.setSystemPrompt(snapshot.systemPrompt());
        entity.setKnowledgeBaseIdsJson(writeJson(snapshot.knowledgeBaseIds()));
        entity.setToolIdsJson(writeJson(snapshot.toolIds()));
        entity.setOwnerUserIdsJson(writeJson(new TreeSet<>(snapshot.visiblePrincipalIds())));
        entity.setAllowedUserIdsJson(writeJson(new TreeSet<>(command.allowedUserIds())));
        entity.setApplicationCreatedAt(snapshot.createdAt());
        entity.setPublishedBy(parseRequiredLong(command.actorId(), "发布人 ID"));
        entity.setReason(command.reason());
        entity.setTraceId(command.traceId());
        entity.setPublishedAt(command.occurredAt());
        return entity;
    }

    public AiApplicationRelease toDomain(AppApplicationReleasePersistenceEntity entity) {
        Objects.requireNonNull(entity, "应用发布版本行不能为空");
        TenantId tenantId = TenantId.of(requiredLong(entity.getTenantId(), "租户 ID"));
        String applicationId = requiredLong(entity.getApplicationId(), "应用 ID");
        AiApplication snapshot = new AiApplication(
                tenantId,
                applicationId,
                AiApplicationType.valueOf(requireText(entity.getApplicationType(), "应用类型")),
                requireText(entity.getApplicationName(), "应用名称"),
                defaultText(entity.getDescription(), "未填写应用描述"),
                requiredLong(entity.getModelConfigId(), "模型配置 ID"),
                readList(entity.getKnowledgeBaseIdsJson(), "知识库 ID 快照"),
                readList(entity.getToolIdsJson(), "工具 ID 快照"),
                Set.copyOf(readList(entity.getOwnerUserIdsJson(), "所有者快照")),
                defaultText(entity.getSystemPrompt(), "请基于已配置知识回答"),
                ApplicationPublicationStatus.PUBLISHED,
                Objects.requireNonNull(entity.getApplicationCreatedAt(), "应用创建时间不能为空"),
                Objects.requireNonNull(entity.getPublishedAt(), "发布时间不能为空")
        );
        return new AiApplicationRelease(
                requiredLong(entity.getId(), "发布版本 ID"),
                tenantId,
                applicationId,
                requiredLongValue(entity.getVersionNo(), "发布版本号"),
                entity.getSourceReleaseId() == null ? null : String.valueOf(entity.getSourceReleaseId()),
                requireText(entity.getRequestId(), "发布请求 ID"),
                ApplicationReleaseOperation.valueOf(requireText(entity.getOperation(), "发布操作")),
                snapshot,
                Set.copyOf(readList(entity.getAllowedUserIdsJson(), "发布受众快照")),
                requiredLong(entity.getPublishedBy(), "发布人 ID"),
                requireText(entity.getReason(), "发布原因"),
                requireText(entity.getTraceId(), "Trace ID"),
                entity.getPublishedAt()
        );
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("应用发布快照 JSON 序列化失败", exception);
        }
    }

    private List<String> readList(String json, String fieldName) {
        try {
            List<String> values = jsonMapper.readValue(requireText(json, fieldName), STRING_LIST);
            if (values == null) {
                throw new IllegalStateException(fieldName + "不能为空");
            }
            return values;
        } catch (JacksonException exception) {
            throw new IllegalStateException(fieldName + " JSON 解析失败", exception);
        }
    }

    private static long requiredLongValue(Long value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalStateException(fieldName + "必须大于 0");
        }
        return value;
    }

    private static String requiredLong(Long value, String fieldName) {
        return String.valueOf(requiredLongValue(value, fieldName));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
