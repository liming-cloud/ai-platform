package com.aiplatform.identity.application;

import com.aiplatform.identity.application.port.PermissionRequestRepository;
import com.aiplatform.identity.application.error.IdentityErrorCodes;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestQuery;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestSensitiveTextPolicy;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestStatus;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionRequestApplicationService {
    private static final ErrorCode REQUEST_NOT_VISIBLE = ErrorCode.of("AIP-IAM-RESOURCE-5004", "权限申请不可见");
    private static final String AUDIT_RESOURCE_TYPE = "IAM_PERMISSION_REQUEST";
    private static final String AUDIT_ACTION = "PERMISSION_REQUEST_DECIDED";
    private static final String RISK_HIGH = "HIGH";
    private final PermissionRequestRepository repository;
    private final Clock clock;
    private final ResourceAclCommandPort resourceAclCommandPort;
    private final AuditEventPublisher auditPublisher;

    public PermissionRequestApplicationService(
            PermissionRequestRepository repository,
            Clock clock,
            ResourceAclCommandPort resourceAclCommandPort,
            AuditEventPublisher auditPublisher
    ) {
        this.repository = Objects.requireNonNull(repository, "权限申请仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.resourceAclCommandPort = Objects.requireNonNull(resourceAclCommandPort, "资源 ACL 命令端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计事件发布端口不能为空");
    }

    @Transactional
    public PermissionRequest create(TenantId tenantId, String actorId, String resourceType, String resourceId,
                                    String permissionCode, String reason) {
        var now = clock.instant();
        TenantId requiredTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        PermissionRequest request = new PermissionRequest(
                requiredTenantId, UUID.randomUUID().toString(), actorId,
                resourceType, resourceId, permissionCode,
                PermissionRequestSensitiveTextPolicy.requireSafeNewText(reason, "申请原因"),
                PermissionRequestStatus.PENDING,
                null, null, now, now
        );
        PermissionRequest persisted = Objects.requireNonNull(
                repository.save(request),
                "权限申请保存结果不能为空"
        );
        return requirePersistedIdentity(requiredTenantId, request.requestId(), persisted);
    }

    @Transactional(readOnly = true)
    public PermissionRequestPage list(TenantId tenantId, String status, int page, int pageSize) {
        TenantId requiredTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        PermissionRequestPage result = Objects.requireNonNull(
                repository.findPage(new PermissionRequestQuery(
                        requiredTenantId,
                        parseStatus(status),
                        page,
                        pageSize
                )),
                "权限申请查询结果不能为空"
        );
        List<PermissionRequest> safeItems = result.items().stream()
                .map(request -> requireVisibleIdentity(requiredTenantId, request == null ? null : request.requestId(), request))
                .toList();
        return new PermissionRequestPage(safeItems, result.total(), result.page(), result.pageSize());
    }

    @Transactional
    public PermissionRequest decide(
            TenantId tenantId,
            String actorId,
            String requestId,
            String action,
            String comment,
            String traceId
    ) {
        TenantId requiredTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        String requiredActorId = requireText(actorId, "处理人 ID");
        String requiredRequestId = requireText(requestId, "申请 ID");
        String safeComment = PermissionRequestSensitiveTextPolicy.requireSafeNewText(comment, "处理意见");
        String requiredTraceId = requireText(traceId, "链路追踪 ID");
        PermissionRequest request = repository.findById(requiredTenantId, requiredRequestId)
                .orElseThrow(() -> new ApplicationException(REQUEST_NOT_VISIBLE, "权限申请不可见"));
        PermissionRequest safeRequest = requireVisibleIdentity(requiredTenantId, requiredRequestId, request);
        if (safeRequest.requesterId().equals(requiredActorId)) {
            throw new ApplicationException(
                    IdentityErrorCodes.PERMISSION_REQUEST_DECISION_DENIED,
                    "申请人不能处理自己的权限申请"
            );
        }
        PermissionRequest decided = safeRequest.decide(action, requiredActorId, safeComment, clock.instant());
        PermissionRequest persisted = requirePersistedIdentity(
                requiredTenantId,
                requiredRequestId,
                Objects.requireNonNull(repository.save(decided), "权限申请保存结果不能为空")
        );
        if (persisted.status() != decided.status()) {
            throw new IllegalStateException("权限申请保存结果状态不一致");
        }
        if (persisted.status() == PermissionRequestStatus.APPROVED) {
            resourceAclCommandPort.grantAllowingSubject(
                    persisted.tenantId(), persisted.resourceType(), persisted.resourceId(),
                    new PermissionCode(persisted.permissionCode()),
                    new ResourceAclSubject("user", persisted.requesterId()),
                    requiredActorId
            );
        }
        publishDecisionAudit(persisted, requiredActorId, requiredTraceId);
        return persisted;
    }

    private void publishDecisionAudit(PermissionRequest request, String actorId, String traceId) {
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                request.tenantId().value(),
                actorId,
                AUDIT_RESOURCE_TYPE,
                request.requestId(),
                AUDIT_ACTION,
                RISK_HIGH,
                null,
                traceId,
                request.updatedAt(),
                Map.of(
                        "permissionRequestId", request.requestId(),
                        "decision", request.status().name(),
                        "requesterId", request.requesterId(),
                        "permissionCode", request.permissionCode(),
                        "targetResourceType", request.resourceType(),
                        "targetResourceId", request.resourceId()
                )
        ));
    }

    private static PermissionRequest requireVisibleIdentity(
            TenantId tenantId,
            String requestId,
            PermissionRequest request
    ) {
        if (request == null
                || !tenantId.equals(request.tenantId())
                || !Objects.equals(requestId, request.requestId())) {
            throw new ApplicationException(REQUEST_NOT_VISIBLE, "权限申请不可见");
        }
        return PermissionRequestSensitiveTextPolicy.sanitizeHistorical(request);
    }

    private static PermissionRequest requirePersistedIdentity(
            TenantId tenantId,
            String requestId,
            PermissionRequest persisted
    ) {
        if (!tenantId.equals(persisted.tenantId()) || !requestId.equals(persisted.requestId())) {
            throw new IllegalStateException("权限申请保存结果身份不一致");
        }
        return PermissionRequestSensitiveTextPolicy.sanitizeHistorical(persisted);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static PermissionRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PermissionRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的权限申请状态：" + status.trim());
        }
    }
}
