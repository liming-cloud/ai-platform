package org.fujay.ai.platform.tenant.application.port;

public interface TenantPasswordHashPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
