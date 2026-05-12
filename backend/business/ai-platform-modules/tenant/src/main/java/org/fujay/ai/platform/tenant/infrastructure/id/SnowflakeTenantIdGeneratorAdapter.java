package org.fujay.ai.platform.tenant.infrastructure.id;

import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.tenant.application.port.TenantIdGeneratorPort;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeTenantIdGeneratorAdapter implements TenantIdGeneratorPort {

    @Override
    public Long nextId() {
        return SnowflakeIdGenerator.nextId();
    }
}
