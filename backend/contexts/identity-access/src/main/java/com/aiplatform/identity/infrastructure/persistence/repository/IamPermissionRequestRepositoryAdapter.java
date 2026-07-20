package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.application.port.PermissionRequestRepository;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestQuery;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestSensitiveTextPolicy;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestStatus;
import com.aiplatform.identity.infrastructure.persistence.entity.IamPermissionRequestPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamPermissionRequestMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class IamPermissionRequestRepositoryAdapter implements PermissionRequestRepository {
    private final IamPermissionRequestMapper mapper;

    public IamPermissionRequestRepositoryAdapter(IamPermissionRequestMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "权限申请 Mapper 不能为空");
    }

    @Override
    public PermissionRequest save(PermissionRequest request) {
        PermissionRequest safeRequest = PermissionRequestSensitiveTextPolicy.sanitizeHistorical(
                Objects.requireNonNull(request, "权限申请不能为空")
        );
        IamPermissionRequestPersistenceEntity entity = toEntity(safeRequest);
        int affected = safeRequest.status() == PermissionRequestStatus.PENDING
                ? mapper.insertRequest(entity)
                : mapper.updateDecision(entity);
        if (affected != 1) throw new IllegalStateException("权限申请保存失败或已被处理");
        IamPermissionRequestPersistenceEntity persisted = mapper.findById(entity.getTenantId(), entity.getRequestId());
        if (persisted == null) {
            throw new IllegalStateException("权限申请保存后未返回持久化结果");
        }
        return toDomain(persisted, entity.getTenantId(), entity.getRequestId());
    }

    @Override
    public Optional<PermissionRequest> findById(TenantId tenantId, String requestId) {
        Long expectedTenantId = databaseId(tenantId);
        String expectedRequestId = requireText(requestId, "申请 ID");
        return Optional.ofNullable(mapper.findById(expectedTenantId, expectedRequestId))
                .map(entity -> toDomain(entity, expectedTenantId, expectedRequestId));
    }

    @Override
    public PermissionRequestPage findPage(PermissionRequestQuery query) {
        PermissionRequestQuery requiredQuery = Objects.requireNonNull(query, "权限申请分页查询不能为空");
        Long expectedTenantId = databaseId(requiredQuery.tenantId());
        String status = requiredQuery.status() == null ? null : requiredQuery.status().name();
        long total = mapper.countByTenant(expectedTenantId, status);
        if (total < 0) throw new IllegalStateException("权限申请总数不能小于 0");
        List<IamPermissionRequestPersistenceEntity> rows = mapper.findPage(
                expectedTenantId,
                status,
                requiredQuery.offset(),
                requiredQuery.pageSize()
        );
        if (rows == null) throw new IllegalStateException("权限申请查询结果不能为空");
        Set<String> requestIds = new HashSet<>();
        List<PermissionRequest> items = rows.stream().map(entity -> {
            String requestId = requireText(entity.getRequestId(), "申请 ID");
            if (!requestIds.add(requestId)) {
                throw new IllegalStateException("权限申请持久化结果身份不一致");
            }
            return toDomain(entity, expectedTenantId, requestId);
        }).toList();
        return new PermissionRequestPage(items, total, requiredQuery.page(), requiredQuery.pageSize());
    }

    private IamPermissionRequestPersistenceEntity toEntity(PermissionRequest request) {
        IamPermissionRequestPersistenceEntity entity = new IamPermissionRequestPersistenceEntity();
        entity.setTenantId(databaseId(request.tenantId()));
        entity.setRequestId(request.requestId());
        entity.setRequesterId(parseRequiredLong(request.requesterId(), "申请人 ID"));
        entity.setResourceType(request.resourceType()); entity.setResourceId(request.resourceId());
        entity.setPermissionCode(request.permissionCode()); entity.setReason(request.reason());
        entity.setStatus(request.status().name());
        entity.setReviewerId(request.reviewerId() == null ? null : parseRequiredLong(request.reviewerId(), "处理人 ID"));
        entity.setDecisionComment(request.decisionComment());
        entity.setCreatedAt(request.createdAt()); entity.setUpdatedAt(request.updatedAt());
        return entity;
    }

    private PermissionRequest toDomain(
            IamPermissionRequestPersistenceEntity entity,
            Long expectedTenantId,
            String expectedRequestId
    ) {
        requireIdentity(entity, expectedTenantId, expectedRequestId);
        return new PermissionRequest(TenantId.of(String.valueOf(entity.getTenantId())), entity.getRequestId(),
                String.valueOf(entity.getRequesterId()), entity.getResourceType(), entity.getResourceId(),
                entity.getPermissionCode(), PermissionRequestSensitiveTextPolicy.sanitizeHistoricalText(entity.getReason()),
                PermissionRequestStatus.valueOf(entity.getStatus()),
                entity.getReviewerId() == null ? null : String.valueOf(entity.getReviewerId()),
                PermissionRequestSensitiveTextPolicy.sanitizeHistoricalText(entity.getDecisionComment()),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static void requireIdentity(
            IamPermissionRequestPersistenceEntity entity,
            Long expectedTenantId,
            String expectedRequestId
    ) {
        if (entity == null
                || !Objects.equals(expectedTenantId, entity.getTenantId())
                || !Objects.equals(expectedRequestId, entity.getRequestId())) {
            throw new IllegalStateException("权限申请持久化结果身份不一致");
        }
    }

    private static Long databaseId(TenantId tenantId) {
        return parseRequiredLong(Objects.requireNonNull(tenantId, "租户 ID 不能为空").value(), "租户 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
