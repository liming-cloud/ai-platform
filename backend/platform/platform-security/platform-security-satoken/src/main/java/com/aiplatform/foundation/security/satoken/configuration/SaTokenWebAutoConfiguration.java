package com.aiplatform.foundation.security.satoken.configuration;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.security.satoken.web.SaTokenPlatformContextArgumentResolver;
import com.aiplatform.foundation.security.satoken.web.SaTokenTrustedContextArgumentResolver;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
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
@ConditionalOnBean(EntityIdGenerator.class)
public class SaTokenWebAutoConfiguration {

    @Bean
    @ConditionalOnBean(AuthenticatedSessionGateway.class)
    SaTokenTrustedContextArgumentResolver saTokenTrustedContextArgumentResolver(
            AuthenticatedSessionGateway sessions,
            EntityIdGenerator identifiers) {
        return new SaTokenTrustedContextArgumentResolver(sessions, identifiers);
    }

    @Bean
    @ConditionalOnBean(AuthenticatedSessionGateway.class)
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

    @Bean
    @ConditionalOnBean(PlatformSessionGateway.class)
    SaTokenPlatformContextArgumentResolver saTokenPlatformContextArgumentResolver(
            PlatformSessionGateway sessions,
            EntityIdGenerator identifiers) {
        return new SaTokenPlatformContextArgumentResolver(sessions, identifiers);
    }

    @Bean
    @ConditionalOnBean(PlatformSessionGateway.class)
    WebMvcConfigurer saTokenPlatformContextWebConfigurer(
            SaTokenPlatformContextArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(
                    List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }
}
