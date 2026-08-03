package com.aiplatform.foundation.security.satoken.web;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.security.web.BrowserSessionCookieNames;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 只从服务端平台 Sa-Token 会话恢复平台身份，不接受请求中的范围字段。
 */
public final class SaTokenPlatformContextArgumentResolver
        implements HandlerMethodArgumentResolver {

    private final PlatformSessionGateway sessions;
    private final EntityIdGenerator identifiers;

    public SaTokenPlatformContextArgumentResolver(
            PlatformSessionGateway sessions,
            EntityIdGenerator identifiers) {
        this.sessions = Objects.requireNonNull(sessions, "平台会话网关不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(PlatformRequestContext.class);
    }

    @Override
    public PlatformRequestContext resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binderFactory) {
        var session = sessions.resolve(BrowserSessionTokenResolver.require(
                request, BrowserSessionCookieNames.PLATFORM_SESSION));
        return new PlatformRequestContext(
                session.subjectId(),
                session.credentialType(),
                session.channel(),
                session.permissionVersion(),
                session.permissions(),
                TraceIds.next(identifiers));
    }
}
