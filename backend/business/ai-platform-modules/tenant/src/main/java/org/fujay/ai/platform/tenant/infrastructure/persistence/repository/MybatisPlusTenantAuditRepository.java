package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuditRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.AuditLogMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.AuditLogPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusTenantAuditRepository implements TenantAuditRepository {

    private final AuditLogMapper auditLogMapper;

    public MybatisPlusTenantAuditRepository(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void append(Long tenantId, Long actorId, String action, String result, String detail) {
        AuditLogPO po = new AuditLogPO();
        po.setId(SnowflakeIdGenerator.nextId());
        po.setTenantId(tenantId);
        po.setActorId(actorId);
        po.setAction(action);
        po.setResult(result);
        po.setDetail(detail);
        po.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(po);
    }
}
