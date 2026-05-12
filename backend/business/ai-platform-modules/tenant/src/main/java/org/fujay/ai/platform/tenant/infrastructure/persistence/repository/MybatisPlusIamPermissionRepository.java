package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.repository.IamPermissionRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamPermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamPermissionPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusIamPermissionRepository implements IamPermissionRepository {

    private final IamPermissionMapper permissionMapper;

    public MybatisPlusIamPermissionRepository(IamPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Override
    public List<IamPermission> list(Long tenantId) {
        return permissionMapper.selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .orderByAsc(IamPermissionPO::getPermissionCode))
                .stream()
                .map(TenantPersistenceConverter::toIamPermission)
                .toList();
    }

    @Override
    public Optional<IamPermission> findByCode(Long tenantId, String permissionCode) {
        return Optional.ofNullable(permissionByCode(tenantId, permissionCode))
                .map(TenantPersistenceConverter::toIamPermission);
    }

    @Override
    public List<IamPermission> findByCodes(Long tenantId, List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .in(IamPermissionPO::getPermissionCode, permissionCodes)
                        .orderByAsc(IamPermissionPO::getPermissionCode))
                .stream()
                .map(TenantPersistenceConverter::toIamPermission)
                .toList();
    }

    @Override
    public Optional<IamPermission> save(IamPermission permission) {
        IamPermissionPO po = permissionById(permission.tenantId(), permission.id());
        if (po == null) {
            if (codeUsedByOtherPermission(permission.tenantId(), permission.permissionCode(), permission.id())) {
                return Optional.empty();
            }
            po = new IamPermissionPO();
            po.setId(permission.id());
            po.setTenantId(permission.tenantId());
            po.setPermissionCode(permission.permissionCode());
            po.setCreatedAt(LocalDateTime.now());
        }
        po.setPermissionName(permission.permissionName());
        po.setStatus(permission.status());
        po.setUpdatedAt(LocalDateTime.now());
        persistPermission(po);
        return Optional.of(TenantPersistenceConverter.toIamPermission(po));
    }

    private IamPermissionPO permissionById(Long tenantId, Long permissionId) {
        return permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .eq(IamPermissionPO::getId, permissionId));
    }

    private IamPermissionPO permissionByCode(Long tenantId, String permissionCode) {
        return permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .eq(IamPermissionPO::getPermissionCode, permissionCode));
    }

    private boolean codeUsedByOtherPermission(Long tenantId, String permissionCode, Long permissionId) {
        IamPermissionPO same = permissionByCode(tenantId, permissionCode);
        return same != null && (permissionId == null || !same.getId().equals(permissionId));
    }

    private void persistPermission(IamPermissionPO permission) {
        if (permissionMapper.selectById(permission.getId()) == null) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }
    }
}
