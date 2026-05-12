package org.fujay.ai.platform.model.application.port;

public interface CurrentPrincipalPort {

    Long currentTenantId();

    void requireAuthenticated();
}
