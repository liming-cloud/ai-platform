package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.PlatformAccountGovernance;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权编排平台账户状态变更及全范围会话撤销。 */
public class PlatformAccountGovernanceApplicationService {

    private final PlatformAccountStore store;
    private final PersonSessionFamilyRevoker sessions;
    private final PasswordHasher passwords;

    /** 构造平台账户治理应用服务。 */
    public PlatformAccountGovernanceApplicationService(
            PlatformAccountStore store, PersonSessionFamilyRevoker sessions, PasswordHasher passwords) {
        this.store = Objects.requireNonNull(store, "平台账户存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "主体会话族撤销器不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码摘要器不能为空");
    }

    /** 禁用目标账户并撤销其全范围会话族。 */
    @Transactional
    public void disable(PlatformRequestContext context, long subjectId) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
            throw new IllegalArgumentException("当前主体没有账户治理权限");
        }
        var targetId = new SubjectId(subjectId);
        var target = store.lock(targetId)
                .orElseThrow(() -> new IllegalArgumentException("目标账户不存在"));
        var result = PlatformAccountGovernance.disable(
                new SubjectId(context.subjectId().value()),
                target,
                store.hasActiveAccountAdministratorRole(targetId),
                store.countOtherActiveAccountAdministrators(targetId));
        store.update(result.identity());
        sessions.revokeAll(result.sessionRevocationSubjectId());
    }

    /** 启用目标账户并撤销其全范围会话族，防止旧会话恢复已失效状态。 */
    @Transactional
    public void enable(PlatformRequestContext context, long subjectId) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
            throw new IllegalArgumentException("当前主体没有账户治理权限");
        }
        var targetId = new SubjectId(subjectId);
        var target = store.lock(targetId)
                .orElseThrow(() -> new IllegalArgumentException("目标账户不存在"));
        var result = PlatformAccountGovernance.enable(
                new SubjectId(context.subjectId().value()), target);
        store.update(result.identity());
        sessions.revokeAll(result.sessionRevocationSubjectId());
    }

    /**
     * 重置另一主体的凭据并撤销其全范围会话族。
     *
     * @throws IllegalArgumentException 当当前主体没有账户治理权限、目标不存在或试图重置自身凭据时拒绝。
     */
    @Transactional
    public void resetPassword(PlatformRequestContext context, long subjectId, char[] replacementPassword) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        replacementPassword = Objects.requireNonNull(replacementPassword, "新口令不能为空");
        try {
            if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
                throw new IllegalArgumentException("当前主体没有账户治理权限");
            }
            var targetId = new SubjectId(subjectId);
            var target = store.lock(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("目标账户不存在"));
            var result = PlatformAccountGovernance.resetPassword(
                    new SubjectId(context.subjectId().value()),
                    target,
                    new EncodedPassword(passwords.encode(replacementPassword)));
            store.update(result.identity());
            sessions.revokeAll(result.sessionRevocationSubjectId());
        } finally {
            Arrays.fill(replacementPassword, '\0');
        }
    }
}
