package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.password.Pbkdf2PasswordAdapter;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 注册认证用例及其密码验证实现。
 */
@AutoConfiguration(after = OrganizationAccessPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.organization-access.enabled",
        havingValue = "true")
public class OrganizationAccessCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Pbkdf2PasswordAdapter pbkdf2PasswordAdapter() {
        return new Pbkdf2PasswordAdapter(new SecureRandom());
    }

    @Bean
    @ConditionalOnMissingBean
    PersonAuthenticationService personAuthenticationService(
            SubjectIdentityRepository identities,
            TenantMembershipRepository memberships,
            AuthenticatedSessionGateway sessions,
            Pbkdf2PasswordAdapter passwords) {
        var dummyPassword = "不存在成员的等价验证口令".toCharArray();
        try {
            return new PersonAuthenticationService(
                    identities,
                    memberships,
                    passwords,
                    sessions,
                    passwords.encode(dummyPassword));
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAuthenticationService platformAuthenticationService(
            SubjectIdentityRepository identities,
            PlatformAccessGrantRepository grants,
            PlatformSessionGateway sessions,
            Pbkdf2PasswordAdapter passwords) {
        var dummyPassword = "不存在主体的等价验证口令".toCharArray();
        try {
            return new PlatformAuthenticationService(
                    identities,
                    grants,
                    passwords,
                    sessions,
                    passwords.encode(dummyPassword));
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleBackfillService tenantLifecycleBackfillService(
            TenantLifecycleBackfillStore store) {
        return new TenantLifecycleBackfillService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantProvisioningService tenantProvisioningService(
            TenantProvisioningStore store,
            EntityIdGenerator identifiers) {
        return new TenantProvisioningService(store, identifiers, Clock.systemUTC());
    }
}
