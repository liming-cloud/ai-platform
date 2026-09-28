package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantAnchorEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLifecycleEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLifecycleOutboxEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantAdministratorRoleEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantQuotaEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantFeatureSettingsEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLoginSecurityPolicyEntity;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantAnchorMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleOutboxMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantAdministratorRoleMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantFeatureSettingsMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginSecurityPolicyMapper;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import java.util.Objects;

/** 单表写入均交给 MyBatis-Plus；提交顺序只满足外键，状态、角色与配额来自领域操作结果。 */
public final class PostgresTenantProvisioningStore implements TenantProvisioningStore {
    private final SubjectIdentityMapper identities;
    private final TenantAnchorMapper anchors;
    private final TenantProvisioningMapper lifecycles;
    private final TenantMembershipMapper memberships;
    private final TenantAdministratorRoleMapper roles;
    private final TenantQuotaMapper quotas;
    private final TenantFeatureSettingsMapper features;
    private final TenantLoginSecurityPolicyMapper security;
    private final TenantLifecycleOutboxMapper outbox;
    private final EntityIdGenerator identifiers;

    /** 构造单表映射依赖；不允许可空或旧新并行存储路径。 */
    public PostgresTenantProvisioningStore(SubjectIdentityMapper identities, TenantAnchorMapper anchors,
            TenantProvisioningMapper lifecycles, TenantMembershipMapper memberships,
            TenantAdministratorRoleMapper roles, TenantQuotaMapper quotas, TenantFeatureSettingsMapper features,
            TenantLoginSecurityPolicyMapper security,
            TenantLifecycleOutboxMapper outbox, EntityIdGenerator identifiers) {
        this.identities = Objects.requireNonNull(identities);
        this.anchors = Objects.requireNonNull(anchors);
        this.lifecycles = Objects.requireNonNull(lifecycles);
        this.memberships = Objects.requireNonNull(memberships);
        this.roles = Objects.requireNonNull(roles);
        this.quotas = Objects.requireNonNull(quotas);
        this.features = Objects.requireNonNull(features);
        this.security = Objects.requireNonNull(security);
        this.outbox = Objects.requireNonNull(outbox);
        this.identifiers = Objects.requireNonNull(identifiers);
    }

    @Override
    public boolean authenticationReady() {
        return lifecycles.authenticationReady();
    }

    @Override
    public void commit(TenantProvisioningResult result) {
        var identity = result.administratorIdentity();
        var row = new SubjectIdentityEntity();
        row.setSubjectId(identity.id().value());
        row.setLoginName(identity.loginName());
        row.setPasswordHash(identity.encodedPassword().verificationValue());
        row.setStatus(identity.status().name());
        row.setVersion(identity.version());
        row.setCreatedAt(identity.createdAt());
        try {
            requireWrite(identities.insert(row));
        } catch (DuplicateKeyException conflict) {
            // 不携带原 SQL 异常链，避免将密码摘要带入日志或响应。
            throw new ApplicationException(TenantOnboardingErrorCode.IDENTITY_CONFLICT);
        } catch (DataAccessException failure) {
            // 数据库约束、触发器等失败也可能包含摘要，禁止原始原因链进入全局异常日志。
            throw new ApplicationException(TenantOnboardingErrorCode.IDENTITY_WRITE_FAILED);
        }
        var lifecycle = result.lifecycle();
        try {
            requireWrite(anchors.insert(new TenantAnchorEntity(lifecycle.id().value(), lifecycle.code(),
                    lifecycle.displayName(), "DISABLED", 0L, lifecycle.createdAt(), lifecycle.createdAt())));
        } catch (DuplicateKeyException conflict) {
            throw new ApplicationException(TenantOnboardingErrorCode.TENANT_CONFLICT);
        }
        requireWrite(lifecycles.insert(new TenantLifecycleEntity(lifecycle.id().value(), lifecycle.code(),
                lifecycle.displayName(), lifecycle.initialAdministratorId().value(), lifecycle.status().name(),
                lifecycle.version(), lifecycle.createdAt())));
        var administrator = result.initialAdministrator();
        var membership = new TenantMembershipEntity();
        membership.setMembershipId(administrator.id().value());
        membership.setTenantId(administrator.tenantId().value());
        membership.setSubjectId(administrator.subjectId().value());
        membership.setMembershipRole(administrator.role().name());
        membership.setStatus(administrator.status().name());
        membership.setPermissionVersion(administrator.permissionVersion());
        membership.setVersion(administrator.version());
        membership.setCreatedAt(administrator.createdAt());
        requireWrite(memberships.insert(membership));
        var role = result.initialAdministratorRoleBinding();
        requireWrite(roles.insert(new TenantAdministratorRoleEntity(role.tenantId().value(),
                role.membershipId().value(), role.role().name(), role.boundAt())));
        requireWrite(quotas.insert(TenantQuotaEntity.from(result.quotas())));
        requireWrite(features.insert(TenantFeatureSettingsEntity.from(
                TenantFeatureSettings.defaults(lifecycle.id()))));
        requireWrite(security.insert(TenantLoginSecurityPolicyEntity.from(
                TenantLoginSecurityPolicy.defaults(lifecycle.id()))));
        var change = result.lifecycleChange();
        requireWrite(outbox.insert(new TenantLifecycleOutboxEntity(identifiers.nextId(), change.tenantId().value(),
                "TENANT_LIFECYCLE_CHANGED", change.status().name(), change.version(), change.occurredAt())));
    }

    private static void requireWrite(int count) {
        if (count != 1) {
            throw new IllegalStateException("开户事实写入失败");
        }
    }
}
