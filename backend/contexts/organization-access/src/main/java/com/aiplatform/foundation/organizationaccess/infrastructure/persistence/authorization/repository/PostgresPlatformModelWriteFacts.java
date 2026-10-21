package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.security.identity.PlatformModelWriteFacts;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Objects;

/** 组织上下文拥有平台主体及角色事实，模型上下文只消费最小平台事实端口。 */
public final class PostgresPlatformModelWriteFacts implements PlatformModelWriteFacts {
    private final PlatformRoleBindingMapper mapper;

    public PostgresPlatformModelWriteFacts(PlatformRoleBindingMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void requireCurrent(PlatformRequestContext context) {
        Objects.requireNonNull(context, "平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE))
            throw new InvalidAuthenticatedSessionException();
        if (mapper.lockModelWriteSubject(context.subjectId().value()) == null)
            throw new InvalidAuthenticatedSessionException();
        var authorization = new PostgresPlatformRoleBindingRepository(mapper)
                .findActiveAuthorizationBySubject(new SubjectId(context.subjectId().value()))
                .orElseThrow(InvalidAuthenticatedSessionException::new);
        // 组织领域与平台安全使用不同的权限值类型，必须按稳定代码显式映射，不能跨类型比较。
        if (authorization.permissionVersion() != context.permissionVersion()
                || !authorization.permissions().contains(new com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode(
                        PlatformPermissions.MODEL_PLATFORM_WRITE.value())))
            throw new InvalidAuthenticatedSessionException();
    }
}
