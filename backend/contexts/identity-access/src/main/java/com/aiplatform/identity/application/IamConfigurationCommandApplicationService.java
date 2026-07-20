package com.aiplatform.identity.application;

import com.aiplatform.identity.application.command.ReplaceIamMenuFeaturesCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamRoleDefaultMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.application.command.CreateIamTenantCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantCommand;
import com.aiplatform.identity.application.command.ChangeIamTenantStatusCommand;
import com.aiplatform.identity.application.port.IamConfigurationCommandPort;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IAM 配置命令应用服务，负责写接口事务、持久化端口调用和安全审计发布。
 */
@Service
public class IamConfigurationCommandApplicationService {
    private static final String RISK_MEDIUM = "MEDIUM";
    private static final String RISK_HIGH = "HIGH";

    private final IamConfigurationCommandPort commandPort;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    public IamConfigurationCommandApplicationService(
            IamConfigurationCommandPort commandPort,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.commandPort = Objects.requireNonNull(commandPort, "IAM 配置写入端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计事件发布端口不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional
    public String createTenant(CreateIamTenantCommand command) {
        Objects.requireNonNull(command, "租户创建命令不能为空");
        String tenantId = commandPort.createTenant(command);
        audit(tenantId, command.actorId(), "IAM_TENANT", tenantId, "IAM_TENANT_CREATE", RISK_HIGH, command.traceId());
        return tenantId;
    }

    @Transactional
    public void updateTenant(UpdateIamTenantCommand command) {
        Objects.requireNonNull(command, "租户更新命令不能为空");
        commandPort.updateTenant(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_TENANT", command.tenantId().value(), "IAM_TENANT_UPDATE", RISK_HIGH, command.traceId());
    }

    @Transactional
    public void changeTenantStatus(ChangeIamTenantStatusCommand command) {
        Objects.requireNonNull(command, "租户状态命令不能为空");
        commandPort.changeTenantStatus(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_TENANT", command.tenantId().value(), "IAM_TENANT_STATUS_CHANGE", RISK_HIGH, command.traceId(),
                Map.of("status", command.status(), "reason", command.reason()));
    }

    @Transactional
    public String createMenu(CreateIamMenuCommand command) {
        Objects.requireNonNull(command, "菜单创建命令不能为空");
        String menuId = commandPort.createMenu(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_MENU", menuId,
                "IAM_MENU_CREATE", RISK_MEDIUM, command.traceId());
        return menuId;
    }

    @Transactional
    public void updateMenu(UpdateIamMenuCommand command) {
        Objects.requireNonNull(command, "菜单更新命令不能为空");
        commandPort.updateMenu(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_MENU", command.menuId(),
                "IAM_MENU_UPDATE", RISK_MEDIUM, command.traceId());
    }

    @Transactional
    public void replaceMenuFeatures(ReplaceIamMenuFeaturesCommand command) {
        Objects.requireNonNull(command, "菜单功能绑定命令不能为空");
        commandPort.replaceMenuFeatures(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_MENU_FEATURE", command.menuId(),
                "IAM_MENU_FEATURE_UPDATE", RISK_MEDIUM, command.traceId());
    }

    @Transactional
    public void updateTenantFeature(UpdateIamTenantFeatureCommand command) {
        Objects.requireNonNull(command, "租户功能更新命令不能为空");
        commandPort.updateTenantFeature(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_TENANT_FEATURE", null,
                "IAM_TENANT_FEATURE_UPDATE", RISK_HIGH, command.traceId(),
                Map.of("featureCode", command.featureCode(), "enabled", command.enabled(), "reason", command.reason()));
    }

    @Transactional
    public void replaceRoleMenus(ReplaceIamRoleMenusCommand command) {
        Objects.requireNonNull(command, "角色菜单矩阵命令不能为空");
        commandPort.replaceRoleMenus(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_ROLE_MENU", command.roleId(),
                "IAM_ROLE_MENU_UPDATE", RISK_HIGH, command.traceId());
    }

    @Transactional
    public void replaceRolePermissions(ReplaceIamRolePermissionsCommand command) {
        Objects.requireNonNull(command, "角色权限矩阵命令不能为空");
        commandPort.replaceRolePermissions(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_ROLE_PERMISSION", command.roleId(),
                "IAM_ROLE_PERMISSION_UPDATE", RISK_HIGH, command.traceId(),
                Map.of("permissionCodes", command.permissionCodes(), "reason", command.reason()));
    }

    @Transactional
    public void updateRoleDefaultMenu(UpdateIamRoleDefaultMenuCommand command) {
        Objects.requireNonNull(command, "角色默认首页命令不能为空");
        commandPort.updateRoleDefaultMenu(command);
        audit(command.tenantId().value(), command.actorId(), "IAM_ROLE", command.roleId(),
                "IAM_ROLE_DEFAULT_MENU_UPDATE", RISK_MEDIUM, command.traceId());
    }

    private void audit(
            String tenantId,
            String actorId,
            String resourceType,
            String resourceId,
            String action,
            String riskLevel,
            String traceId
    ) {
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                tenantId,
                actorId,
                resourceType,
                resourceId,
                action,
                riskLevel,
                null,
                traceId,
                Instant.now(clock)
        ));
    }

    private void audit(
            String tenantId,
            String actorId,
            String resourceType,
            String resourceId,
            String action,
            String riskLevel,
            String traceId,
            Map<String, Object> details
    ) {
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                tenantId,
                actorId,
                resourceType,
                resourceId,
                action,
                riskLevel,
                null,
                traceId,
                Instant.now(clock),
                details
        ));
    }
}
