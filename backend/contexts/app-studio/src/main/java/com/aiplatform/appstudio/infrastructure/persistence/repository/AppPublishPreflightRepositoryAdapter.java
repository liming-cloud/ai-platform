package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.port.AppPublishPreflightRepository;
import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.infrastructure.persistence.converter.AppPublishPreflightPersistenceConverter;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppPublishPreflightPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppPublishPreflightMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AppPublishPreflightRepositoryAdapter implements AppPublishPreflightRepository {
    private final AppPublishPreflightMapper mapper;
    private final AppPublishPreflightPersistenceConverter converter;

    public AppPublishPreflightRepositoryAdapter(
            AppPublishPreflightMapper mapper,
            AppPublishPreflightPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "发布预检 Mapper 不能为空");
        this.converter = Objects.requireNonNull(converter, "发布预检转换器不能为空");
    }

    @Override
    public Optional<AppPublishPreflightResult> findByRequestId(
            TenantId tenantId,
            String applicationId,
            String publishRequestId
    ) {
        AppPublishPreflightPersistenceEntity entity = mapper.findByRequestId(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                parseRequiredLong(applicationId, "应用 ID"),
                publishRequestId
        );
        return Optional.ofNullable(entity).map(converter::toDomain);
    }

    @Override
    public AppPublishPreflightResult save(AppPublishPreflightResult result) {
        mapper.insertResult(converter.fromDomain(result));
        return findByRequestId(result.tenantId(), result.applicationId(), result.publishRequestId())
                .orElseThrow(() -> new IllegalStateException("发布预检结果保存失败"));
    }
}
