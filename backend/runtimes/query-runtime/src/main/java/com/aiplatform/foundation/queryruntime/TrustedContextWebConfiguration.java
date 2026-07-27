package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把服务端会话恢复接入在线查询方法参数。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
public class TrustedContextWebConfiguration implements WebMvcConfigurer {

    private final QueryTrustedContextArgumentResolver resolver;

    public TrustedContextWebConfiguration(
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers) {
        resolver = new QueryTrustedContextArgumentResolver(sessions, identifiers);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
