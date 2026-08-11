package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
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

/** 受权建立无初始角色的平台账户，并只将初始口令交给摘要端口。 */
public class PlatformAccountEstablishmentApplicationService {

    private final PlatformAccountStore store;
    private final PasswordHasher passwords;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    /** 构造平台账户建立用例。 */
    public PlatformAccountEstablishmentApplicationService(
            PlatformAccountStore store, PasswordHasher passwords, EntityIdGenerator identifiers) {
        this(store, passwords, identifiers, Clock.systemUTC());
    }

    PlatformAccountEstablishmentApplicationService(
            PlatformAccountStore store, PasswordHasher passwords, EntityIdGenerator identifiers, Clock clock) {
        this.store = Objects.requireNonNull(store, "平台账户存储不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码摘要器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 建立默认无固定角色的活动平台账户。
     *
     * @throws IllegalArgumentException 当调用主体未获授权、登录名或口令不合法，或全局登录名已存在时拒绝。
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
                throw new IllegalArgumentException("全局登录名已存在");
            }
            return new PlatformAccountSummary(
                    identity.id().value(),
                    identity.loginName(),
                    identity.status().name(),
                    identity.version(),
                    identity.createdAt(),
                    Set.of());
        } finally {
            Arrays.fill(initialPassword, '\0');
        }
    }
}
