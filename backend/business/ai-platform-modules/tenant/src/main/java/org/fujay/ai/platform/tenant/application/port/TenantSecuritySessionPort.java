package org.fujay.ai.platform.tenant.application.port;

public interface TenantSecuritySessionPort {

    TenantLoginToken login(CurrentTenantPrincipal principal);

    CurrentTenantPrincipal currentPrincipal();

    void logout();
}
