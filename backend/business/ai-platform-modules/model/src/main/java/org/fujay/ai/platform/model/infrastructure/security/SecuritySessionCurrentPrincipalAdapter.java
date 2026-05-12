package org.fujay.ai.platform.model.infrastructure.security;

import org.fujay.ai.platform.model.application.port.CurrentPrincipalPort;
import org.fujay.ai.platform.security.SecuritySessionFacade;
import org.springframework.stereotype.Component;

@Component
public class SecuritySessionCurrentPrincipalAdapter implements CurrentPrincipalPort {

    private final SecuritySessionFacade securitySessionFacade;

    public SecuritySessionCurrentPrincipalAdapter(SecuritySessionFacade securitySessionFacade) {
        this.securitySessionFacade = securitySessionFacade;
    }

    @Override
    public Long currentTenantId() {
        return securitySessionFacade.currentPrincipal().tenantId().value();
    }

    @Override
    public void requireAuthenticated() {
        securitySessionFacade.currentPrincipal();
    }
}
