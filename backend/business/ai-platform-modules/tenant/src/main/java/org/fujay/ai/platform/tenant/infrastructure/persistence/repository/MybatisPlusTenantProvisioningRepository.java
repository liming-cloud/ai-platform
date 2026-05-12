package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.model.DefaultMenu;
import org.fujay.ai.platform.tenant.domain.model.DefaultPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.model.UserCredential;
import org.fujay.ai.platform.tenant.domain.repository.TenantProvisioningRepository;
import org.fujay.ai.platform.tenant.domain.service.IamBootstrapCatalog;
import org.fujay.ai.platform.tenant.infrastructure.persistence.converter.TenantPersistenceConverter;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamPermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRolePermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserCredentialMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.PlatformTenantMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamPermissionPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRoleMenuPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamRolePermissionPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserCredentialPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserPO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.IamUserRolePO;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.PlatformTenantPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusTenantProvisioningRepository implements TenantProvisioningRepository {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final PlatformTenantMapper tenantMapper;
    private final IamUserMapper userMapper;
    private final IamUserCredentialMapper credentialMapper;
    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;
    private final IamMenuMapper menuMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamRoleMenuMapper roleMenuMapper;
    private final IamUserRoleMapper userRoleMapper;

    public MybatisPlusTenantProvisioningRepository(
            PlatformTenantMapper tenantMapper,
            IamUserMapper userMapper,
            IamUserCredentialMapper credentialMapper,
            IamRoleMapper roleMapper,
            IamPermissionMapper permissionMapper,
            IamMenuMapper menuMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamRoleMenuMapper roleMenuMapper,
            IamUserRoleMapper userRoleMapper) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public void provisionTenant(Tenant tenant, TenantUser adminUser, UserCredential credential) {
        saveTenant(tenant);
        TenantContext.runWith(new TenantId(tenant.id()), () -> {
            TenantUser savedAdmin = saveUser(adminUser);
            saveCredential(credential);
            bootstrapAccessControl(tenant.id(), savedAdmin.id(), LocalDateTime.now());
        });
    }

    private Tenant saveTenant(Tenant tenant) {
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

    private TenantUser saveUser(TenantUser user) {
        LocalDateTime now = LocalDateTime.now();
        IamUserPO po = userMapper.selectById(user.id());
        if (po == null) {
            po = new IamUserPO();
            po.setId(user.id());
            po.setTenantId(user.tenantId());
            po.setUsername(user.username());
            po.setCreatedAt(now);
        }
        po.setDisplayName(user.displayName());
        po.setStatus(user.status());
        po.setSystemAccount(user.systemAccount());
        po.setUpdatedAt(now);
        if (userMapper.selectById(po.getId()) == null) {
            userMapper.insert(po);
        } else {
            userMapper.updateById(po);
        }
        return TenantPersistenceConverter.toTenantUser(po);
    }

    private void saveCredential(UserCredential credential) {
        IamUserCredentialPO po = credentialMapper.selectById(credential.id());
        if (po == null) {
            po = new IamUserCredentialPO();
            po.setId(credential.id());
            po.setTenantId(credential.tenantId());
            po.setUserId(credential.userId());
        }
        po.setPasswordHash(credential.passwordHash());
        po.setUpdatedAt(LocalDateTime.now());
        if (credentialMapper.selectById(po.getId()) == null) {
            credentialMapper.insert(po);
        } else {
            credentialMapper.updateById(po);
        }
    }

    private void bootstrapAccessControl(Long tenantId, Long userId, LocalDateTime now) {
        IamRolePO role = upsertPlatformAdminRole(tenantId, now);
        IamBootstrapCatalog.permissions().forEach(permission -> {
            IamPermissionPO po = upsertDefaultPermission(tenantId, permission, now);
            upsertRolePermission(tenantId, role.getId(), po.getId(), now);
        });
        IamBootstrapCatalog.menus().forEach(menu -> {
            IamMenuPO po = upsertDefaultMenu(tenantId, menu, now);
            upsertRoleMenu(tenantId, role.getId(), po.getId(), now);
        });
        IamBootstrapCatalog.retiredMenuCodes().forEach(menuCode -> retireMenu(tenantId, menuCode, now));
        upsertUserRole(tenantId, userId, role.getId(), now);
    }

    private IamRolePO upsertPlatformAdminRole(Long tenantId, LocalDateTime now) {
        IamRolePO po = roleByCode(tenantId, IamRole.PLATFORM_ADMIN_ROLE_CODE);
        if (po == null) {
            po = new IamRolePO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setTenantId(tenantId);
            po.setRoleCode(IamRole.PLATFORM_ADMIN_ROLE_CODE);
            po.setCreatedAt(now);
        }
        po.setRoleName("平台管理员");
        po.setStatus(STATUS_ENABLED);
        po.setUpdatedAt(now);
        if (roleMapper.selectById(po.getId()) == null) {
            roleMapper.insert(po);
        } else {
            roleMapper.updateById(po);
        }
        return po;
    }

    private IamPermissionPO upsertDefaultPermission(Long tenantId, DefaultPermission permission, LocalDateTime now) {
        IamPermissionPO po = permissionByCode(tenantId, permission.permissionCode());
        if (po == null) {
            po = new IamPermissionPO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setTenantId(tenantId);
            po.setPermissionCode(permission.permissionCode());
            po.setCreatedAt(now);
        }
        po.setPermissionName(permission.permissionName());
        po.setStatus(STATUS_ENABLED);
        po.setUpdatedAt(now);
        if (permissionMapper.selectById(po.getId()) == null) {
            permissionMapper.insert(po);
        } else {
            permissionMapper.updateById(po);
        }
        return po;
    }

    private IamMenuPO upsertDefaultMenu(Long tenantId, DefaultMenu menu, LocalDateTime now) {
        IamMenuPO po = menuByCode(tenantId, menu.menuCode());
        if (po == null) {
            po = new IamMenuPO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setTenantId(tenantId);
            po.setMenuCode(menu.menuCode());
            po.setCreatedAt(now);
        }
        po.setMenuCode(menu.menuCode());
        po.setMenuName(menu.menuName());
        po.setParentCode(menu.parentCode());
        po.setRoutePath(menu.routePath());
        po.setComponentKey(menu.componentKey());
        po.setPlatformScope(menu.platformScope());
        po.setSortOrder(menu.sortOrder());
        po.setStatus(STATUS_ENABLED);
        po.setUpdatedAt(now);
        if (menuMapper.selectById(po.getId()) == null) {
            menuMapper.insert(po);
        } else {
            menuMapper.updateById(po);
        }
        return po;
    }

    private void retireMenu(Long tenantId, String menuCode, LocalDateTime now) {
        IamMenuPO menu = menuByCode(tenantId, menuCode);
        if (menu == null || !STATUS_ENABLED.equals(menu.getStatus())) {
            return;
        }
        menu.setStatus(STATUS_DISABLED);
        menu.setUpdatedAt(now);
        menuMapper.updateById(menu);
    }

    private IamRolePO roleByCode(Long tenantId, String roleCode) {
        return roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                .eq(IamRolePO::getTenantId, tenantId)
                .eq(IamRolePO::getRoleCode, roleCode));
    }

    private IamPermissionPO permissionByCode(Long tenantId, String permissionCode) {
        return permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .eq(IamPermissionPO::getPermissionCode, permissionCode));
    }

    private IamMenuPO menuByCode(Long tenantId, String menuCode) {
        return menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .eq(IamMenuPO::getMenuCode, menuCode));
    }

    private void upsertUserRole(Long tenantId, Long userId, Long roleId, LocalDateTime now) {
        if (userRoleMapper.selectOne(Wrappers.<IamUserRolePO>lambdaQuery()
                .eq(IamUserRolePO::getTenantId, tenantId)
                .eq(IamUserRolePO::getUserId, userId)
                .eq(IamUserRolePO::getRoleId, roleId)) != null) {
            return;
        }
        IamUserRolePO po = new IamUserRolePO();
        po.setId(SnowflakeIdGenerator.nextId());
        po.setTenantId(tenantId);
        po.setUserId(userId);
        po.setRoleId(roleId);
        po.setCreatedAt(now);
        userRoleMapper.insert(po);
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
