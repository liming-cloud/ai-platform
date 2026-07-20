package com.aiplatform.modelhub.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestClaim;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestId;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestRepository;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResourceType;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResultSnapshot;
import com.aiplatform.modelhub.infrastructure.persistence.entity.ModelCreationRequestPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelCreationRequestMapper;
import com.aiplatform.shared.kernel.Sha256Digests;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter whose insert-on-conflict claim serializes concurrent requests on the unique key. */
@Repository
public class ModelCreationRequestRepositoryAdapter implements ModelCreationRequestRepository {
    private final ModelCreationRequestMapper mapper;

    public ModelCreationRequestRepositoryAdapter(ModelCreationRequestMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型创建请求 Mapper 不能为空");
    }

    @Override
    public ModelCreationRequestClaim claim(
            TenantId tenantId,
            ModelCreationResourceType resourceType,
            String requestId,
            String requestDigest
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(resourceType, "模型创建资源类型不能为空");
        String normalizedRequestId = ModelCreationRequestId.require(requestId);
        String normalizedDigest = Sha256Digests.requireLowercase(requestDigest, "模型创建请求摘要");
        ModelCreationRequestPersistenceEntity request = new ModelCreationRequestPersistenceEntity();
        request.setTenantId(parseRequiredLong(tenantId.value(), "租户 ID"));
        request.setResourceType(resourceType.name());
        request.setRequestId(normalizedRequestId);
        request.setRequestDigest(normalizedDigest);
        Long insertedId = mapper.insertClaim(request);
        if (insertedId != null) {
            return ModelCreationRequestClaim.acquired(normalizedDigest);
        }
        ModelCreationRequestPersistenceEntity existing = mapper.findClaim(
                request.getTenantId(), request.getResourceType(), normalizedRequestId
        );
        if (existing == null) {
            throw new IllegalStateException("模型创建请求并发冲突后未找到既有记录");
        }
        if (existing.getCompletedAt() == null) {
            throw new IllegalStateException("模型创建请求存在未完成的已提交记录");
        }
        ModelCreationResourceType storedResourceType;
        try {
            storedResourceType = ModelCreationResourceType.valueOf(existing.getResourceType());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("模型创建请求持久化资源类型不合法", exception);
        }
        if (storedResourceType != resourceType) {
            throw new IllegalStateException("模型创建请求持久化资源类型与查询键不匹配");
        }
        ModelCreationResultSnapshot result = new ModelCreationResultSnapshot(
                storedResourceType,
                nullableId(existing.getResultParentResourceId()),
                nullableId(existing.getResultResourceId()),
                existing.getResultResourceCode(),
                existing.getResultModelType(),
                existing.getResultStatus(),
                existing.getResultApprovalRequestId(),
                existing.getResultConnectivityStatus()
        );
        return ModelCreationRequestClaim.replay(
                existing.getRequestDigest(),
                result
        );
    }

    @Override
    public void complete(
            TenantId tenantId,
            ModelCreationResourceType resourceType,
            String requestId,
            String requestDigest,
            ModelCreationResultSnapshot result
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(resourceType, "模型创建资源类型不能为空");
        Objects.requireNonNull(result, "模型创建结果快照不能为空");
        if (result.resourceType() != resourceType) {
            throw new IllegalArgumentException("模型创建结果快照类型与请求资源类型不匹配");
        }
        ModelCreationRequestPersistenceEntity request = new ModelCreationRequestPersistenceEntity();
        request.setTenantId(parseRequiredLong(tenantId.value(), "租户 ID"));
        request.setResourceType(resourceType.name());
        request.setRequestId(ModelCreationRequestId.require(requestId));
        request.setRequestDigest(Sha256Digests.requireLowercase(requestDigest, "模型创建请求摘要"));
        request.setResultParentResourceId(nullableRequiredLong(result.parentResourceId(), "模型创建结果父资源 ID"));
        request.setResultResourceId(parseRequiredLong(result.resourceId(), "模型创建结果资源 ID"));
        request.setResultResourceCode(result.resourceCode());
        request.setResultModelType(result.modelType());
        request.setResultStatus(result.status());
        request.setResultApprovalRequestId(result.approvalRequestId());
        request.setResultConnectivityStatus(result.connectivityStatus());
        int updated = mapper.completeClaim(request);
        if (updated != 1) {
            throw new IllegalStateException("模型创建请求完成写入未命中唯一待完成记录");
        }
    }

    private static String nullableId(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long nullableRequiredLong(String value, String fieldName) {
        return value == null ? null : parseRequiredLong(value, fieldName);
    }
}
