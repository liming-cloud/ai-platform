package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 从授权头恢复服务端会话，不接受调用方提交租户标识。
 */
public final class QueryTrustedContextArgumentResolver
        implements HandlerMethodArgumentResolver {

    private static final String PREFIX = "Bearer ";

    private final AuthenticatedSessionGateway sessions;

    public QueryTrustedContextArgumentResolver(
            AuthenticatedSessionGateway sessions) {
        this.sessions = Objects.requireNonNull(sessions, "认证会话网关不能为空");
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
        var session = sessions.resolve(requireBearer(
                request.getHeader(HttpHeaders.AUTHORIZATION)));
        return new TrustedRequestContext(
                session.tenantId(),
                session.subjectId(),
                session.membershipId(),
                session.credentialType(),
                session.channel(),
                session.permissionVersion(),
                session.permissions(),
                UUID.randomUUID().toString());
    }

    private static String requireBearer(String value) {
        if (value == null
                || !value.startsWith(PREFIX)
                || value.length() <= PREFIX.length()
                || value.length() > 519) {
            throw new com.aiplatform.foundation.organizationaccess.adapter.out.session
                    .InvalidAuthenticatedSessionException();
        }
        var token = value.substring(PREFIX.length());
        if (token.isBlank() || !token.equals(token.trim())) {
            throw new com.aiplatform.foundation.organizationaccess.adapter.out.session
                    .InvalidAuthenticatedSessionException();
        }
        return token;
    }
}
