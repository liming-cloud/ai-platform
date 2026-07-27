package com.aiplatform.foundation.security.satoken.configuration;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.foundation.security.satoken.session.SaTokenSessionGateway;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
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
            SaTokenSecurityProperties properties) {
        return new SaTokenSessionGateway(
                new StpLogic(properties.personLoginType()),
                saTokenDao,
                properties.sessionTimeout());
    }
}
