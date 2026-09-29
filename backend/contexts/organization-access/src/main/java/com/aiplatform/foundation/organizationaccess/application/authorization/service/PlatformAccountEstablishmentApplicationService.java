package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.exception.PlatformAccountLoginNameConflictException;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/** 在同一事务建立全局身份与平台管理员资格，并只将初始口令交给摘要端口。 */
public class PlatformAccountEstablishmentApplicationService {

    private final PlatformAccountStore store;
    private final PlatformRoleBindingStore bindings;
    private final PasswordHasher passwords;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    /** 构造平台账户建立用例。 */
    public PlatformAccountEstablishmentApplicationService(
            PlatformAccountStore store, PlatformRoleBindingStore bindings,
            PasswordHasher passwords, EntityIdGenerator identifiers) {
        this(store, bindings, passwords, identifiers, Clock.systemUTC());
    }

    PlatformAccountEstablishmentApplicationService(
            PlatformAccountStore store, PlatformRoleBindingStore bindings,
            PasswordHasher passwords, EntityIdGenerator identifiers, Clock clock) {
        this.store = Objects.requireNonNull(store, "平台账户存储不能为空");
        this.bindings = Objects.requireNonNull(bindings, "平台角色绑定存储不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码摘要器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 原子建立活动身份及平台管理员资格；绑定或审计失败必须回滚新身份。
     *
     * @throws IllegalArgumentException 当调用主体未获授权，或登录名、口令不合法时拒绝。
     * @throws PlatformAccountLoginNameConflictException 当全局登录名已经存在时拒绝。
     */
    @Transactional
    public PlatformAccountSummary establish(PlatformRequestContext context, String loginName, char[] initialPassword) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        initialPassword = Objects.requireNonNull(initialPassword, "初始口令不能为空");
        try {
            if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
                throw new IllegalArgumentException("当前主体没有账户治理权限");
            }
            var identity = SubjectIdentity.register(
                    new SubjectId(identifiers.nextId()),
                    loginName,
                    new EncodedPassword(passwords.encode(initialPassword)),
                    clock.instant());
            if (!store.insert(identity)) {
                throw new PlatformAccountLoginNameConflictException();
            }
            bindings.bind(PlatformRoleBinding.bind(
                    new PlatformRoleBindingId(identifiers.nextId()),
                    identity.id(),
                    PlatformRole.PLATFORM_ADMINISTRATOR,
                    clock.instant()));
            store.recordOperation(context.subjectId().value(), identity.id().value(), "CREATE", identity.version());
            return new PlatformAccountSummary(
                    identity.id().value(),
                    identity.loginName(),
                    identity.status().name(),
                    identity.version(),
                    identity.createdAt(),
                    Set.of(PlatformRole.PLATFORM_ADMINISTRATOR.name()));
        } finally {
            Arrays.fill(initialPassword, '\0');
        }
    }
}
