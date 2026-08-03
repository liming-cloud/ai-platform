package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import com.aiplatform.foundation.organizationaccess.domain.tenant.service.TenantProvisioning;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台受权的新租户建立用例。
 *
 * <p>应用层只恢复平台上下文、锁定既有初始管理员、分配标识、调用领域服务并提交结果；成员角色和
 * 生命周期不变量不在此处重新实现。</p>
 */
public class TenantProvisioningService {

    private final TenantProvisioningStore store;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public TenantProvisioningService(
            TenantProvisioningStore store,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "租户建立存储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 建立一个尚未激活的新租户。
     *
     * @throws IllegalArgumentException 当平台上下文无治理权限或初始管理员不可用时拒绝。
     */
    @Transactional
    public TenantProvisioningResult provision(
            PlatformRequestContext platform,
            TenantProvisioningCommand command) {
        platform = Objects.requireNonNull(platform, "平台请求上下文不能为空");
        command = Objects.requireNonNull(command, "租户建立命令不能为空");
        if (!platform.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new IllegalArgumentException("平台上下文缺少租户治理权限");
        }
        var administrator = store.lockActiveInitialAdministrator(command.initialAdministratorId())
                .orElseThrow(() -> new IllegalArgumentException("初始管理员不可用"));
        var occurredAt = Instant.now(clock);
        var lifecycle = TenantLifecycle.provision(
                new TenantId(identifiers.nextId()),
                command.tenantCode(),
                command.displayName(),
                administrator.id(),
                occurredAt);
        var result = TenantProvisioning.provision(
                lifecycle,
                administrator,
                new MemberId(identifiers.nextId()),
                occurredAt);
        store.commit(result);
        return result;
    }

    /**
     * 为 HTTP 等接口层建立租户，并只返回应用读取视图。
     */
    public TenantLifecycleView provision(
            PlatformRequestContext platform,
            String tenantCode,
            String displayName,
            long initialAdministratorSubjectId) {
        var result = provision(
                platform,
                new TenantProvisioningCommand(
                        tenantCode,
                        displayName,
                        new SubjectId(initialAdministratorSubjectId)));
        return TenantLifecycleView.from(result.lifecycle());
    }
}
