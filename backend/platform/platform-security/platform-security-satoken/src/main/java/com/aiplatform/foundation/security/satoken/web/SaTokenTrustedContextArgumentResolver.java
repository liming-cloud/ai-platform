package com.aiplatform.foundation.security.satoken.web;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TenantFeatureAvailability;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.web.BrowserSessionCookieNames;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 从服务端 Sa-Token 会话恢复可信身份，不接受请求体中的身份和租户字段。
 */
public final class SaTokenTrustedContextArgumentResolver
        implements HandlerMethodArgumentResolver {

    private final AuthenticatedSessionGateway sessions;
    private final EntityIdGenerator identifiers;
    private final TenantFeatureAvailability features;

    public SaTokenTrustedContextArgumentResolver(
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers) {
        this(sessions, identifiers, null);
    }

    public SaTokenTrustedContextArgumentResolver(
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers,
            TenantFeatureAvailability features) {
        this.sessions = Objects.requireNonNull(sessions, "认证会话网关不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.features = features;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(TrustedRequestContext.class);
    }

    @Override
    public TrustedRequestContext resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binderFactory) {
        var session = sessions.resolve(BrowserSessionTokenResolver.require(
                request, BrowserSessionCookieNames.TENANT_SESSION));
        if (parameter != null) {
            var method = parameter.getMethod();
            var required = method == null ? null : method.getAnnotation(RequiresTenantFeature.class);
            if (required == null) required = parameter.getContainingClass().getAnnotation(RequiresTenantFeature.class);
            if (required != null) {
                if (features == null) throw new IllegalStateException("租户功能检查器未装配");
                features.requireAvailable(session.tenantId(), required.value());
            }
        }
        return new TrustedRequestContext(
                session.tenantId(),
                session.subjectId(),
                session.membershipId(),
                session.credentialType(),
                session.channel(),
                session.permissionVersion(),
                session.permissions(),
                TraceIds.next(identifiers));
    }
}
