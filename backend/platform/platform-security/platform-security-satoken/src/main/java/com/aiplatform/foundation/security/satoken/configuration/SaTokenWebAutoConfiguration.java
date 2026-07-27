package com.aiplatform.foundation.security.satoken.configuration;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.security.satoken.web.SaTokenTrustedContextArgumentResolver;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把 Sa-Token 可信身份恢复统一接入 Spring MVC。
 */
@AutoConfiguration(after = SaTokenSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({
    AuthenticatedSessionGateway.class,
    EntityIdGenerator.class
})
public class SaTokenWebAutoConfiguration {

    @Bean
    SaTokenTrustedContextArgumentResolver saTokenTrustedContextArgumentResolver(
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers) {
        return new SaTokenTrustedContextArgumentResolver(sessions, identifiers);
    }

    @Bean
    WebMvcConfigurer saTokenTrustedContextWebConfigurer(
            SaTokenTrustedContextArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(
                    List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }
}
