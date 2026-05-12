package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.shared.tenant.TenantIdentityResolver;
import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.repository.TenantRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.PlatformTenantMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.PlatformTenantPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusTenantRepository implements TenantRepository, TenantIdentityResolver {

    private final PlatformTenantMapper tenantMapper;

    public MybatisPlusTenantRepository(PlatformTenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public Optional<Tenant> findByCode(String tenantCode) {
        return Optional.ofNullable(tenantMapper.selectOne(Wrappers.<PlatformTenantPO>lambdaQuery()
                        .eq(PlatformTenantPO::getTenantCode, tenantCode)))
                .map(TenantPersistenceConverter::toTenant);
    }

    @Override
    public Optional<TenantId> findTenantIdByCode(String tenantCode) {
        return findByCode(tenantCode).map(tenant -> new TenantId(tenant.id()));
    }

    @Override
    public Optional<Tenant> findById(Long tenantId) {
        return Optional.ofNullable(tenantMapper.selectById(tenantId)).map(TenantPersistenceConverter::toTenant);
    }

    @Override
    public List<Tenant> list() {
        return tenantMapper
                .selectList(Wrappers.<PlatformTenantPO>lambdaQuery().orderByAsc(PlatformTenantPO::getTenantCode))
                .stream()
                .map(TenantPersistenceConverter::toTenant)
                .toList();
    }

    @Override
    public Tenant save(Tenant tenant) {
        LocalDateTime now = LocalDateTime.now();
        PlatformTenantPO po = tenantMapper.selectById(tenant.id());
        if (po == null) {
            po = new PlatformTenantPO();
            po.setId(tenant.id());
            po.setTenantCode(tenant.tenantCode());
            po.setCreatedAt(now);
        }
        po.setTenantName(tenant.tenantName());
        po.setStatus(tenant.status());
        po.setUpdatedAt(now);
        if (tenantMapper.selectById(po.getId()) == null) {
            tenantMapper.insert(po);
        } else {
            tenantMapper.updateById(po);
        }
        return TenantPersistenceConverter.toTenant(po);
    }
}
