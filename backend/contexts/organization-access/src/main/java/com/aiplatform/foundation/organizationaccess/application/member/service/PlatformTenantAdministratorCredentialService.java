package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.PlatformAccountGovernance;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 只按目标租户的管理员资格定位全局身份并受控重置凭据。 */
public class PlatformTenantAdministratorCredentialService {

    private final PlatformTenantAdministratorStore administrators;
    private final PlatformAccountStore accounts;
    private final PersonSessionFamilyRevoker sessions;
    private final PasswordHasher passwords;

    /** 构造租户管理员凭据治理用例。 */
    public PlatformTenantAdministratorCredentialService(
            PlatformTenantAdministratorStore administrators,
            PlatformAccountStore accounts,
            PersonSessionFamilyRevoker sessions,
            PasswordHasher passwords) {
        this.administrators = Objects.requireNonNull(administrators, "租户管理员存储不能为空");
        this.accounts = Objects.requireNonNull(accounts, "全局身份存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "会话族撤销器不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码摘要器不能为空");
    }

    /**
     * 在目标成员资格仍属该租户且版本未变时重置全局密码，随后撤销所有旧会话。
     *
     * @throws IllegalArgumentException 当目标不属于该租户、不是管理员或版本已过期时拒绝。
     */
    @Transactional
    public void resetPassword(
            PlatformRequestContext context,
            String tenantId,
            long membershipId,
            long expectedVersion,
            char[] replacementPassword) {
        replacementPassword = Objects.requireNonNull(replacementPassword, "新口令不能为空");
        try {
            context = Objects.requireNonNull(context, "可信平台上下文不能为空");
            if (!context.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
                throw new IllegalArgumentException("当前主体没有租户管理员治理权限");
            }
            var targetTenant = TenantId.parse(tenantId);
            var targetMember = new MemberId(membershipId);
            administrators.lockTenantGovernanceScope(targetTenant);
            var membership = administrators.lockMembership(targetTenant, targetMember)
                    .orElseThrow(() -> new IllegalArgumentException("租户管理员不可用"));
            if (!membership.tenantId().equals(targetTenant)
                    || !membership.id().equals(targetMember)
                    || membership.role() != TenantMembershipRole.TENANT_ADMINISTRATOR
                    || membership.version() != expectedVersion) {
                throw new IllegalArgumentException("租户管理员资格已变更");
            }
            var targetSubject = membership.subjectId();
            var target = accounts.lock(targetSubject)
                    .orElseThrow(() -> new IllegalArgumentException("租户管理员身份不可用"));
            var result = PlatformAccountGovernance.resetPassword(
                    new SubjectId(context.subjectId().value()),
                    target,
                    new EncodedPassword(passwords.encode(replacementPassword)));
            accounts.update(result.identity());
            sessions.revokeAll(result.sessionRevocationSubjectId());
        } finally {
            Arrays.fill(replacementPassword, '\0');
        }
    }
}
