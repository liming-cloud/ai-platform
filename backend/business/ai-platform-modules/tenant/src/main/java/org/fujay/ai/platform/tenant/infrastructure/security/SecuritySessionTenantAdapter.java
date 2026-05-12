package org.fujay.ai.platform.tenant.infrastructure.security;

import org.fujay.ai.platform.security.AuthPrincipal;
import org.fujay.ai.platform.security.LoginToken;
import org.fujay.ai.platform.security.SecuritySessionFacade;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.tenant.application.port.CurrentTenantPrincipal;
import org.fujay.ai.platform.tenant.application.port.TenantLoginToken;
import org.fujay.ai.platform.tenant.application.port.TenantSecuritySessionPort;
import org.springframework.stereotype.Component;

@Component
public class SecuritySessionTenantAdapter implements TenantSecuritySessionPort {

    private final SecuritySessionFacade securitySessionFacade;

    public SecuritySessionTenantAdapter(SecuritySessionFacade securitySessionFacade) {
        this.securitySessionFacade = securitySessionFacade;
    }

    @Override
    public TenantLoginToken login(CurrentTenantPrincipal principal) {
        LoginToken token = securitySessionFacade.login(new AuthPrincipal(
                new TenantId(principal.tenantId()), principal.actorType(), principal.actorId()));
        return new TenantLoginToken(token.tokenName(), token.tokenValue());
    }

    @Override
    public CurrentTenantPrincipal currentPrincipal() {
        AuthPrincipal principal = securitySessionFacade.currentPrincipal();
        return new CurrentTenantPrincipal(principal.tenantId().value(), principal.actorType(), principal.actorId());
    }

    @Override
    public void logout() {
        securitySessionFacade.logout();
    }
}
