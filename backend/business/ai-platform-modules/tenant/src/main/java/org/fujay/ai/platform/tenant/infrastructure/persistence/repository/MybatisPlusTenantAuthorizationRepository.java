package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuthorizationRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamPermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRolePermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamPermissionPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRoleMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePermissionPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserRolePO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusTenantAuthorizationRepository implements TenantAuthorizationRepository {

    private static final String STATUS_ENABLED = "ENABLED";

    private final IamUserRoleMapper userRoleMapper;
    private final IamRoleMapper roleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamRoleMenuMapper roleMenuMapper;
    private final IamPermissionMapper permissionMapper;
    private final IamMenuMapper menuMapper;

    public MybatisPlusTenantAuthorizationRepository(
            IamUserRoleMapper userRoleMapper,
            IamRoleMapper roleMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamRoleMenuMapper roleMenuMapper,
            IamPermissionMapper permissionMapper,
            IamMenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
    }

    @Override
    public List<IamRole> findRolesByUserId(Long tenantId, Long userId) {
        List<Long> roleIds = roleIdsByUserId(tenantId, userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(Wrappers.<IamRolePO>lambdaQuery()
                        .eq(IamRolePO::getTenantId, tenantId)
                        .in(IamRolePO::getId, roleIds)
                        .eq(IamRolePO::getStatus, STATUS_ENABLED))
                .stream()
                .map(TenantPersistenceConverter::toIamRole)
                .toList();
    }

    @Override
    public List<IamPermission> findPermissionsByUserId(Long tenantId, Long userId) {
        List<Long> roleIds = roleIdsByUserId(tenantId, userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(Wrappers.<IamRolePermissionPO>lambdaQuery()
                        .eq(IamRolePermissionPO::getTenantId, tenantId)
                        .in(IamRolePermissionPO::getRoleId, roleIds))
                .stream()
                .map(IamRolePermissionPO::getPermissionId)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .in(IamPermissionPO::getId, permissionIds)
                        .eq(IamPermissionPO::getStatus, STATUS_ENABLED)
                        .orderByAsc(IamPermissionPO::getPermissionCode))
                .stream()
                .map(TenantPersistenceConverter::toIamPermission)
                .toList();
    }

    @Override
    public List<IamMenu> findMenusByUserId(Long tenantId, Long userId) {
        List<Long> roleIds = roleIdsByUserId(tenantId, userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(Wrappers.<IamRoleMenuPO>lambdaQuery()
                        .eq(IamRoleMenuPO::getTenantId, tenantId)
                        .in(IamRoleMenuPO::getRoleId, roleIds))
                .stream()
                .map(IamRoleMenuPO::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .in(IamMenuPO::getId, menuIds)
                        .eq(IamMenuPO::getStatus, STATUS_ENABLED)
                        .orderByAsc(IamMenuPO::getSortOrder))
                .stream()
                .map(TenantPersistenceConverter::toIamMenu)
                .toList();
    }

    private List<Long> roleIdsByUserId(Long tenantId, Long userId) {
        return userRoleMapper.selectList(Wrappers.<IamUserRolePO>lambdaQuery()
                        .eq(IamUserRolePO::getTenantId, tenantId)
                        .eq(IamUserRolePO::getUserId, userId))
                .stream()
                .map(IamUserRolePO::getRoleId)
                .distinct()
                .toList();
    }
}
