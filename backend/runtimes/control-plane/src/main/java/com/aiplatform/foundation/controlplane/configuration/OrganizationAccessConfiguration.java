package com.aiplatform.foundation.controlplane.configuration;

import com.aiplatform.foundation.organizationaccess.adapter.out.password.Pbkdf2PasswordAdapter;
import com.aiplatform.foundation.organizationaccess.adapter.out.persistence.PostgresTenantMemberCredentialRepository;
import com.aiplatform.foundation.organizationaccess.adapter.out.persistence.TenantMemberCredentialMapper;
import com.aiplatform.foundation.organizationaccess.api.PersonAuthenticationUseCase;
import com.aiplatform.foundation.organizationaccess.application.authentication.DefaultPersonAuthenticationService;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.port.out.TenantMemberCredentialRepository;
import java.security.SecureRandom;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
