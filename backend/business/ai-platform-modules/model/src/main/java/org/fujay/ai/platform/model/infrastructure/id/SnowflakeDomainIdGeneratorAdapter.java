package org.fujay.ai.platform.model.infrastructure.id;

import org.fujay.ai.platform.model.application.port.DomainIdGeneratorPort;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeDomainIdGeneratorAdapter implements DomainIdGeneratorPort {

    @Override
    public Long nextId() {
        return SnowflakeIdGenerator.nextId();
    }
}
