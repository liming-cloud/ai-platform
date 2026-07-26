package com.aiplatform.foundation.controlplane.configuration;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.foundation.organizationaccess.adapter.out.password.Pbkdf2PasswordAdapter;
import com.aiplatform.foundation.organizationaccess.adapter.out.persistence.PostgresTenantMemberCredentialRepository;
import com.aiplatform.foundation.organizationaccess.adapter.out.persistence.TenantMemberCredentialMapper;
import com.aiplatform.foundation.organizationaccess.adapter.out.session.SaTokenSessionGateway;
import com.aiplatform.foundation.organizationaccess.api.PersonAuthenticationUseCase;
import com.aiplatform.foundation.organizationaccess.application.authentication.DefaultPersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.port.out.TenantMemberCredentialRepository;
import java.security.SecureRandom;
import java.time.Duration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 控制面使用的组织权限上下文显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.organization-access.enabled",
        havingValue = "true",
        matchIfMissing = true)
@MapperScan(basePackageClasses = TenantMemberCredentialMapper.class)
public class OrganizationAccessConfiguration {

    private static final String PERSON_LOGIN_TYPE = "foundation-v1-person";

    @Bean
    TenantMemberCredentialRepository tenantMemberCredentialRepository(
            TenantMemberCredentialMapper mapper) {
        return new PostgresTenantMemberCredentialRepository(mapper);
    }

    @Bean
    Pbkdf2PasswordAdapter pbkdf2PasswordAdapter() {
        return new Pbkdf2PasswordAdapter(new SecureRandom());
    }

    @Bean
    AuthenticatedSessionGateway authenticatedSessionGateway(
            SaTokenDao saTokenDao,
            @Value("${foundation.security.session-timeout:PT1H}") Duration timeout) {
        SaManager.setSaTokenDao(saTokenDao);
        return new SaTokenSessionGateway(new StpLogic(PERSON_LOGIN_TYPE), timeout);
    }

    @Bean
    PersonAuthenticationUseCase personAuthenticationUseCase(
            TenantMemberCredentialRepository members,
            AuthenticatedSessionGateway sessions,
            Pbkdf2PasswordAdapter encoder) {
        var dummyPassword = "不存在成员的等价验证口令".toCharArray();
        try {
            return new DefaultPersonAuthenticationService(
                    members,
                    encoder,
                    sessions,
                    encoder.encode(dummyPassword));
        } finally {
            java.util.Arrays.fill(dummyPassword, '\0');
        }
    }
}
