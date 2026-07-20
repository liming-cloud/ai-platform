package com.aiplatform.appstudio.infrastructure.persistence.converter;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.domain.publish.PublishPreflightIssue;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppPublishPreflightPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AppPublishPreflightPersistenceConverter {
    private static final TypeReference<List<PublishPreflightIssue>> ISSUE_LIST = new TypeReference<>() { };

    private final JsonMapper objectMapper;

    public AppPublishPreflightPersistenceConverter(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public AppPublishPreflightPersistenceEntity fromDomain(AppPublishPreflightResult result) {
        AppPublishPreflightPersistenceEntity entity = new AppPublishPreflightPersistenceEntity();
        entity.setTenantId(parseRequiredLong(result.tenantId().value(), "租户 ID"));
        entity.setApplicationId(parseRequiredLong(result.applicationId(), "应用 ID"));
        entity.setPublishRequestId(result.publishRequestId());
        entity.setCandidateFingerprint(result.candidateFingerprint());
        entity.setPassed(result.passed());
        entity.setBlockersJson(writeIssues(result.blockers()));
        entity.setWarningsJson(writeIssues(result.warnings()));
        entity.setTraceId(result.traceId());
        entity.setCheckedBy(parseRequiredLong(result.checkedBy(), "检查人 ID"));
        entity.setCheckedAt(result.checkedAt());
        entity.setCreatedAt(result.checkedAt());
        entity.setUpdatedAt(result.checkedAt());
        entity.setVersion(0L);
        return entity;
    }

    public AppPublishPreflightResult toDomain(AppPublishPreflightPersistenceEntity entity) {
        return new AppPublishPreflightResult(
                TenantId.of(String.valueOf(entity.getTenantId())),
                String.valueOf(entity.getApplicationId()),
                entity.getPublishRequestId(),
                entity.getCandidateFingerprint(),
                Boolean.TRUE.equals(entity.getPassed()),
                readIssues(entity.getBlockersJson()),
                readIssues(entity.getWarningsJson()),
                entity.getTraceId(),
                String.valueOf(entity.getCheckedBy()),
                entity.getCheckedAt()
        );
    }

    private String writeIssues(List<PublishPreflightIssue> issues) {
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (JacksonException exception) {
            throw new IllegalStateException("发布预检问题序列化失败", exception);
        }
    }

    private List<PublishPreflightIssue> readIssues(String value) {
        try {
            return objectMapper.readValue(value, ISSUE_LIST);
        } catch (JacksonException exception) {
            throw new IllegalStateException("发布预检问题反序列化失败", exception);
        }
    }
}
