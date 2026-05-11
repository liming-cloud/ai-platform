package org.fujay.ai.platform.tenant.infrastructure.bootstrap;

import org.fujay.ai.platform.security.PasswordHasher;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.BootstrapTenant;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.BootstrapUser;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class LocalDevBootstrapRunner implements ApplicationRunner {

    private final LocalBootstrapProperties properties;
    private final TenantAccessRepository repository;
    private final PasswordHasher passwordHasher;

    public LocalDevBootstrapRunner(
            LocalBootstrapProperties properties,
            TenantAccessRepository repository,
            PasswordHasher passwordHasher) {
        this.properties = properties;
        this.repository = repository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        repository.bootstrapTenantUser(
                new BootstrapTenant(properties.getTenantCode(), properties.getTenantName()),
                new BootstrapUser(properties.getAdminUsername(), properties.getAdminDisplayName()),
                passwordHasher.hash(properties.getAdminPassword()));
    }
}
