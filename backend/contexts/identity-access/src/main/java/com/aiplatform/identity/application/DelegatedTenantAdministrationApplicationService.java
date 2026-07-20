package com.aiplatform.identity.application;

import com.aiplatform.identity.application.command.AssignIamTenantAdministratorCommand;
import com.aiplatform.identity.application.command.ExecuteDelegatedTenantActionCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.port.IamConfigurationCommandPort;
import com.aiplatform.identity.application.result.DelegatedTenantActionResult;
import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionRequest;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionType;
import com.aiplatform.identity.domain.port.DelegatedTenantActionRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台管理员代管租户应用服务，保证业务变更、代管记录和审计事件处于同一事务边界。
 */
@Service
public class DelegatedTenantAdministrationApplicationService {
    private static final String AUDIT_ACTION = "IAM_TENANT_DELEGATION_EXECUTED";
    private static final String RISK_HIGH = "HIGH";

    private final IamConfigurationCommandPort configurationCommands;
    private final DelegatedTenantActionRepository actions;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    public DelegatedTenantAdministrationApplicationService(
            IamConfigurationCommandPort configurationCommands,
            DelegatedTenantActionRepository actions,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.configurationCommands = Objects.requireNonNull(configurationCommands, "IAM 配置命令端口不能为空");
        this.actions = Objects.requireNonNull(actions, "代管租户动作仓储不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计事件发布端口不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional
    public DelegatedTenantActionResult execute(ExecuteDelegatedTenantActionCommand command) {
        Objects.requireNonNull(command, "平台代管租户命令不能为空");
        DelegatedTenantActionRequest request = toDomainRequest(command);
        String requestHash = requestHash(command, request);
        return actions.findByRequestHash(command.targetTenantId(), command.operatorId(), requestHash)
                .map(DelegatedTenantActionResult::from)
                .orElseGet(() -> executeNewAction(command, request, requestHash));
    }

    private DelegatedTenantActionResult executeNewAction(
            ExecuteDelegatedTenantActionCommand command,
            DelegatedTenantActionRequest request,
            String requestHash
    ) {
        Instant occurredAt = Instant.now(clock);
        String auditEventId = UUID.randomUUID().toString();
        DelegatedTenantAction action = actions.save(DelegatedTenantAction.succeeded(
                command.targetTenantId(),
                command.operatorId(),
                request,
                requestHash,
                auditEventId,
                occurredAt
        ));
        if (!auditEventId.equals(action.auditEventId())) {
            return DelegatedTenantActionResult.from(action);
        }
        executeBusinessMutation(command, request);
        auditPublisher.publish(new AuditEvent(
                auditEventId,
                command.targetTenantId().value(),
                command.operatorId(),
                request.targetResourceType(),
                request.targetResourceId(),
                AUDIT_ACTION,
                RISK_HIGH,
                null,
                command.traceId(),
                occurredAt,
                Map.of(
                        "actionType", request.actionType().name(),
                        "decision", DelegatedTenantAction.STATUS_SUCCEEDED,
                        "reason", request.reason(),
                        "operatorTenantId", command.operatorTenantId().value(),
                        "delegationActionId", action.actionId()
                )
        ));
        return DelegatedTenantActionResult.from(action);
    }

    private void executeBusinessMutation(
            ExecuteDelegatedTenantActionCommand command,
            DelegatedTenantActionRequest request
    ) {
        switch (request.actionType()) {
            case ASSIGN_TENANT_ADMIN -> configurationCommands.assignTenantAdministrator(
                    new AssignIamTenantAdministratorCommand(
                            command.targetTenantId(),
                            command.operatorId(),
                            command.traceId(),
                            request.targetUserId(),
                            request.targetRoleId()
                    )
            );
            case REPLACE_ROLE_PERMISSIONS -> configurationCommands.replaceRolePermissions(
                    new ReplaceIamRolePermissionsCommand(
                            command.targetTenantId(),
                            command.operatorId(),
                            command.traceId(),
                            request.targetRoleId(),
                            request.permissionCodes(),
                            request.reason()
                    )
            );
        }
    }

    private static DelegatedTenantActionRequest toDomainRequest(ExecuteDelegatedTenantActionCommand command) {
        return new DelegatedTenantActionRequest(
                DelegatedTenantActionType.from(command.actionType()),
                command.targetRoleId(),
                command.targetUserId(),
                command.permissionCodes(),
                command.reason(),
                command.idempotencyKey()
        );
    }

    private static String requestHash(
            ExecuteDelegatedTenantActionCommand command,
            DelegatedTenantActionRequest request
    ) {
        String canonical = String.join("|",
                lengthPrefixed(command.targetTenantId().value()),
                lengthPrefixed(command.operatorId()),
                request.canonicalPayload()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value;
    }
}
