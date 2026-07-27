package com.aiplatform.foundation.controlplane.web.security;

import com.aiplatform.foundation.controlplane.web.auth.BearerTokens;
import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.kernel.identifier.TraceIds;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 从服务端会话恢复可信身份，不接受业务请求中的租户字段。
 */
public final class TrustedContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthenticatedSessionGateway sessions;
    private final EntityIdGenerator identifiers;

    public TrustedContextArgumentResolver(
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers) {
        this.sessions = Objects.requireNonNull(sessions, "认证会话网关不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
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
        var session = sessions.resolve(BearerTokens.require(
                request.getHeader(HttpHeaders.AUTHORIZATION)));
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
