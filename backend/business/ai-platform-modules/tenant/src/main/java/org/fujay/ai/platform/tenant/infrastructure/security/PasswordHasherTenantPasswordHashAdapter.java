package org.fujay.ai.platform.tenant.infrastructure.security;

import org.fujay.ai.platform.security.PasswordHasher;
import org.fujay.ai.platform.tenant.application.port.TenantPasswordHashPort;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasherTenantPasswordHashAdapter implements TenantPasswordHashPort {

    private final PasswordHasher passwordHasher;

    public PasswordHasherTenantPasswordHashAdapter(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordHasher.hash(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordHasher.matches(rawPassword, encodedPassword);
    }
}
