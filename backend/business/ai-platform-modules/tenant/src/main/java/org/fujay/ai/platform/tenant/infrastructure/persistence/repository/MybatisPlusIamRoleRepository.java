package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.model.RoleAccess;
import org.fujay.ai.platform.tenant.domain.repository.IamRoleRepository;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamPermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRolePermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamPermissionPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRoleMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePermissionPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusIamRoleRepository implements IamRoleRepository {

    private static final String STATUS_ENABLED = "ENABLED";

    private final IamRoleMapper roleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamRoleMenuMapper roleMenuMapper;
    private final IamPermissionMapper permissionMapper;
    private final IamMenuMapper menuMapper;

    public MybatisPlusIamRoleRepository(
            IamRoleMapper roleMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamRoleMenuMapper roleMenuMapper,
            IamPermissionMapper permissionMapper,
            IamMenuMapper menuMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
    }

    @Override
    public Optional<IamRole> findById(Long tenantId, Long roleId) {
        return Optional.ofNullable(roleById(tenantId, roleId)).map(TenantPersistenceConverter::toIamRole);
    }

    @Override
    public Optional<IamRole> findByCode(Long tenantId, String roleCode) {
        return Optional.ofNullable(roleByCode(tenantId, roleCode)).map(TenantPersistenceConverter::toIamRole);
    }

    @Override
    public List<IamRole> list(Long tenantId) {
        return roleMapper.selectList(Wrappers.<IamRolePO>lambdaQuery()
                        .eq(IamRolePO::getTenantId, tenantId)
                        .orderByAsc(IamRolePO::getRoleCode))
                .stream()
                .map(TenantPersistenceConverter::toIamRole)
                .toList();
    }

    @Override
    public List<IamPermission> findPermissionsByRoleId(Long tenantId, Long roleId) {
        return permissionsByIds(tenantId, rolePermissionIds(tenantId, List.of(roleId)), false);
    }

    @Override
    public List<IamMenu> findMenusByRoleId(Long tenantId, Long roleId) {
        return menusByIds(tenantId, roleMenuIds(tenantId, List.of(roleId)), false);
    }

    @Override
    public Optional<IamRole> save(IamRole role) {
        IamRolePO po = roleById(role.tenantId(), role.id());
        if (po == null) {
            if (codeUsedByOtherRole(role.tenantId(), role.roleCode(), role.id())) {
                return Optional.empty();
            }
            po = new IamRolePO();
            po.setId(role.id());
            po.setTenantId(role.tenantId());
            po.setRoleCode(role.roleCode());
            po.setCreatedAt(LocalDateTime.now());
        }
        po.setRoleName(role.roleName());
        po.setStatus(role.status());
        po.setUpdatedAt(LocalDateTime.now());
        persistRole(po);
        return Optional.of(TenantPersistenceConverter.toIamRole(po));
    }

    @Override
    public RoleAccess replaceRoleAccess(IamRole role, List<IamPermission> permissions, List<IamMenu> menus) {
        role.ensureRoleAccessMutationAllowed();
        Long tenantId = role.tenantId();
        Long roleId = role.id();
        rolePermissionMapper.delete(Wrappers.<IamRolePermissionPO>lambdaQuery()
                .eq(IamRolePermissionPO::getTenantId, tenantId)
                .eq(IamRolePermissionPO::getRoleId, roleId));
        roleMenuMapper.delete(Wrappers.<IamRoleMenuPO>lambdaQuery()
                .eq(IamRoleMenuPO::getTenantId, tenantId)
                .eq(IamRoleMenuPO::getRoleId, roleId));
        LocalDateTime now = LocalDateTime.now();
        permissions.forEach(permission -> upsertRolePermission(tenantId, roleId, permission.id(), now));
        menus.forEach(menu -> upsertRoleMenu(tenantId, roleId, menu.id(), now));
        return new RoleAccess(role, permissions, menus);
    }

    private IamRolePO roleById(Long tenantId, Long roleId) {
        return roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                .eq(IamRolePO::getTenantId, tenantId)
                .eq(IamRolePO::getId, roleId));
    }

    private IamRolePO roleByCode(Long tenantId, String roleCode) {
        return roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                .eq(IamRolePO::getTenantId, tenantId)
                .eq(IamRolePO::getRoleCode, roleCode));
    }

    private boolean codeUsedByOtherRole(Long tenantId, String roleCode, Long roleId) {
        IamRolePO same = roleByCode(tenantId, roleCode);
        return same != null && (roleId == null || !same.getId().equals(roleId));
    }

    private void persistRole(IamRolePO role) {
        if (roleMapper.selectById(role.getId()) == null) {
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
    }

    private List<Long> rolePermissionIds(Long tenantId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return rolePermissionMapper.selectList(Wrappers.<IamRolePermissionPO>lambdaQuery()
                        .eq(IamRolePermissionPO::getTenantId, tenantId)
                        .in(IamRolePermissionPO::getRoleId, roleIds))
                .stream()
                .map(IamRolePermissionPO::getPermissionId)
                .distinct()
                .toList();
    }

    private List<Long> roleMenuIds(Long tenantId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleMenuMapper.selectList(Wrappers.<IamRoleMenuPO>lambdaQuery()
                        .eq(IamRoleMenuPO::getTenantId, tenantId)
                        .in(IamRoleMenuPO::getRoleId, roleIds))
                .stream()
                .map(IamRoleMenuPO::getMenuId)
                .distinct()
                .toList();
    }

    private List<IamPermission> permissionsByIds(Long tenantId, List<Long> permissionIds, boolean enabledOnly) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        var query = Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .in(IamPermissionPO::getId, permissionIds)
                .orderByAsc(IamPermissionPO::getPermissionCode);
        if (enabledOnly) {
            query.eq(IamPermissionPO::getStatus, STATUS_ENABLED);
        }
        return permissionMapper.selectList(query).stream().map(TenantPersistenceConverter::toIamPermission).toList();
    }

    private List<IamMenu> menusByIds(Long tenantId, List<Long> menuIds, boolean enabledOnly) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        var query = Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .in(IamMenuPO::getId, menuIds)
                .orderByAsc(IamMenuPO::getSortOrder);
        if (enabledOnly) {
            query.eq(IamMenuPO::getStatus, STATUS_ENABLED);
        }
        return menuMapper.selectList(query).stream().map(TenantPersistenceConverter::toIamMenu).toList();
    }

    private void upsertRolePermission(Long tenantId, Long roleId, Long permissionId, LocalDateTime now) {
        if (rolePermissionMapper.selectOne(Wrappers.<IamRolePermissionPO>lambdaQuery()
                .eq(IamRolePermissionPO::getTenantId, tenantId)
                .eq(IamRolePermissionPO::getRoleId, roleId)
                .eq(IamRolePermissionPO::getPermissionId, permissionId)) != null) {
            return;
        }
        IamRolePermissionPO po = new IamRolePermissionPO();
        po.setId(SnowflakeIdGenerator.nextId());
        po.setTenantId(tenantId);
        po.setRoleId(roleId);
        po.setPermissionId(permissionId);
        po.setCreatedAt(now);
        rolePermissionMapper.insert(po);
    }

    private void upsertRoleMenu(Long tenantId, Long roleId, Long menuId, LocalDateTime now) {
        if (roleMenuMapper.selectOne(Wrappers.<IamRoleMenuPO>lambdaQuery()
                .eq(IamRoleMenuPO::getTenantId, tenantId)
                .eq(IamRoleMenuPO::getRoleId, roleId)
                .eq(IamRoleMenuPO::getMenuId, menuId)) != null) {
            return;
        }
        IamRoleMenuPO po = new IamRoleMenuPO();
        po.setId(SnowflakeIdGenerator.nextId());
        po.setTenantId(tenantId);
        po.setRoleId(roleId);
        po.setMenuId(menuId);
        po.setCreatedAt(now);
        roleMenuMapper.insert(po);
    }
}
