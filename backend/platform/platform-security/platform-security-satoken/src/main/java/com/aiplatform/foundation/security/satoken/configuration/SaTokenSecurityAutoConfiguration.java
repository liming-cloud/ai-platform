package com.aiplatform.foundation.security.satoken.configuration;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import com.aiplatform.foundation.security.satoken.session.ContextBoundStpLogic;
import com.aiplatform.foundation.security.satoken.session.SaTokenPlatformSessionGateway;
import com.aiplatform.foundation.security.satoken.session.SaTokenSessionGateway;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 集中装配 Sa-Token 会话实现，业务上下文和运行模块不接触框架类型。
 */
@AutoConfiguration
@EnableConfigurationProperties(SaTokenSecurityProperties.class)
public class SaTokenSecurityAutoConfiguration {

    @Bean
    @ConditionalOnBean(SaTokenDao.class)
    @ConditionalOnMissingBean(AuthenticatedSessionGateway.class)
    AuthenticatedSessionGateway authenticatedSessionGateway(
            SaTokenDao saTokenDao,
            SaTokenConfig saTokenConfig,
            SaTokenSecurityProperties properties) {
        return new SaTokenSessionGateway(
                new ContextBoundStpLogic(
                        properties.personLoginType(),
                        saTokenDao,
                        saTokenConfig),
                properties.sessionTimeout());
    }

    @Bean
    @ConditionalOnBean(SaTokenDao.class)
    @ConditionalOnMissingBean(PlatformSessionGateway.class)
    PlatformSessionGateway platformSessionGateway(
            SaTokenDao saTokenDao,
            SaTokenConfig saTokenConfig,
            SaTokenSecurityProperties properties) {
        return new SaTokenPlatformSessionGateway(
                new ContextBoundStpLogic(
                        properties.platformLoginType(),
                        saTokenDao,
                        saTokenConfig),
                properties.sessionTimeout());
    }
}
