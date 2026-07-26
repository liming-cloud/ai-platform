package com.aiplatform.foundation.controlplane.configuration;

import com.aiplatform.foundation.controlplane.web.security.TrustedContextArgumentResolver;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把认证会话恢复接入控制面方法参数。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AuthenticatedSessionGateway.class)
public class TrustedContextWebConfiguration implements WebMvcConfigurer {

    private final TrustedContextArgumentResolver resolver;

    public TrustedContextWebConfiguration(AuthenticatedSessionGateway sessions) {
        this.resolver = new TrustedContextArgumentResolver(sessions);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
