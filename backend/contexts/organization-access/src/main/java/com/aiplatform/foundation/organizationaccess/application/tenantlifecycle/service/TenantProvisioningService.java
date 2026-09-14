package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.organizationaccess.domain.tenant.service.TenantProvisioning;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台受权的新租户建立用例。
 *
 * <p>唯一公共事务入口覆盖身份、租户、管理员、配额和事件，任何失败均整体回滚。</p>
 */
public class TenantProvisioningService {

    private final TenantProvisioningStore store;
    private final EntityIdGenerator identifiers;
    private final Clock clock;
    private final PasswordHasher passwords;

    /** 构造开户依赖；不依赖平台账户建立服务，避免混淆两类账户职责。 */
    public TenantProvisioningService(
            TenantProvisioningStore store,
            EntityIdGenerator identifiers,
            PasswordHasher passwords,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "租户建立存储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码摘要端口不能为空");
    }

    /**
     * 创建直接可登录的新租户；授权及环境检查先于密码计算和写入。
     *
     * @throws ApplicationException 当操作者无权限、环境未就绪或唯一约束冲突时拒绝。
     * @throws IllegalArgumentException 当开户字段违反领域约束时拒绝。
     */
    @Transactional
    public TenantLifecycleView provision(
            PlatformRequestContext platform,
            TenantProvisioningCommand command, char[] password) {
        Objects.requireNonNull(password, "管理员密码不能为空");
        try {
            Objects.requireNonNull(platform, "平台请求上下文不能为空");
            Objects.requireNonNull(command, "租户建立命令不能为空");
            if (!platform.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
                throw new ApplicationException(TenantOnboardingErrorCode.ACCESS_DENIED);
            }
            if (!store.authenticationReady()) {
                throw new ApplicationException(TenantOnboardingErrorCode.AUTHENTICATION_NOT_READY);
            }
            var occurredAt = clock.instant();
            var tenantId = new TenantId(identifiers.nextId());
            var quotas = new TenantQuota(tenantId, command.knowledgeBases(), command.documentsPerKnowledgeBase(),
                    command.bytesPerKnowledgeBase(), command.maxFileBytes(), command.maxTextBytes(), 0L);
            var administrator = SubjectIdentity.register(new SubjectId(identifiers.nextId()),
                    command.administratorLoginName(), new EncodedPassword(passwords.encode(password)), occurredAt);
            var lifecycle = TenantLifecycle.provision(tenantId, command.tenantCode(), command.displayName(),
                    administrator.id(), occurredAt);
            var result = TenantProvisioning.provision(lifecycle, administrator,
                    new MemberId(identifiers.nextId()), quotas, occurredAt);
            store.commit(result);
            return TenantLifecycleView.from(result.lifecycle());
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
