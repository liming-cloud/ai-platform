package org.fujay.ai.platform.tenant.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.shared.tenant.TenantIdentityResolver;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.BootstrapTenant;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.BootstrapUser;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.CredentialRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.MenuRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.PermissionRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.RoleAccessRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.RoleRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.TenantRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.UserRecord;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.AuditLogMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamPermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRoleMenuMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamRolePermissionMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserCredentialMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.IamUserRoleMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.mapper.PlatformTenantMapper;
import org.fujay.ai.platform.tenant.infrastructure.persistence.po.AuditLogPO;
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
public class MybatisPlusTenantAccessRepository implements TenantAccessRepository, TenantIdentityResolver {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final List<String> RETIRED_MENU_CODES = List.of("platform.rbac");
    private static final List<DefaultPermission> DEFAULT_PERMISSIONS = List.of(
            new DefaultPermission("platform:tenant:read", "查看租户"),
            new DefaultPermission("platform:tenant:write", "维护租户"),
            new DefaultPermission("rbac:role:read", "查看角色"),
            new DefaultPermission("rbac:role:write", "维护角色"),
            new DefaultPermission("rbac:permission:read", "查看权限点"),
            new DefaultPermission("rbac:permission:write", "维护权限点"),
            new DefaultPermission("rbac:menu:read", "查看菜单"),
            new DefaultPermission("rbac:menu:write", "维护菜单"),
            new DefaultPermission("model:platform-catalog:read", "查看平台模型基础配置"),
            new DefaultPermission("model:platform-catalog:write", "维护平台模型基础配置"),
            new DefaultPermission("model:tenant-config:read", "查看租户模型配置"),
            new DefaultPermission("model:tenant-config:write", "维护租户模型配置"));
    private static final List<DefaultMenu> DEFAULT_MENUS = List.of(
            new DefaultMenu("platform.root", "平台管理", null, "/platform", "PlatformLayout", "PLATFORM", 10),
            new DefaultMenu("platform.tenants", "租户管理", "platform.root", "/platform/tenants", "PlatformTenants", "PLATFORM", 11),
            new DefaultMenu("platform.roles", "角色管理", "platform.root", "/platform/roles", "PlatformRoles", "PLATFORM", 12),
            new DefaultMenu(
                    "platform.permissions",
                    "权限管理",
                    "platform.root",
                    "/platform/permissions",
                    "PlatformPermissions",
                    "PLATFORM",
                    13),
            new DefaultMenu("platform.menus", "菜单管理", "platform.root", "/platform/menus", "PlatformMenus", "PLATFORM", 14),
            new DefaultMenu(
                    "platform.model-catalog",
                    "模型基础配置",
                    "platform.root",
                    "/platform/model-catalog",
                    "PlatformModelCatalog",
                    "PLATFORM",
                    15),
            new DefaultMenu("tenant.root", "租户平台", null, "/tenant", "TenantLayout", "TENANT", 20),
            new DefaultMenu("tenant.models", "模型配置", "tenant.root", "/tenant/models", "TenantModels", "TENANT", 21));

    private final PlatformTenantMapper tenantMapper;
    private final IamUserMapper userMapper;
    private final IamUserCredentialMapper credentialMapper;
    private final AuditLogMapper auditLogMapper;
    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;
    private final IamMenuMapper menuMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamRoleMenuMapper roleMenuMapper;

    public MybatisPlusTenantAccessRepository(
            PlatformTenantMapper tenantMapper,
            IamUserMapper userMapper,
            IamUserCredentialMapper credentialMapper,
            AuditLogMapper auditLogMapper,
            IamRoleMapper roleMapper,
            IamPermissionMapper permissionMapper,
            IamMenuMapper menuMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamRoleMenuMapper roleMenuMapper) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.auditLogMapper = auditLogMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public Optional<TenantRecord> findTenantByCode(String tenantCode) {
        return Optional.ofNullable(tenantMapper.selectOne(Wrappers.<PlatformTenantPO>lambdaQuery()
                        .eq(PlatformTenantPO::getTenantCode, tenantCode)))
                .map(this::toTenantRecord);
    }

    @Override
    public Optional<TenantId> findTenantIdByCode(String tenantCode) {
        return findTenantByCode(tenantCode).map(tenant -> new TenantId(tenant.id()));
    }

    @Override
    public Optional<TenantRecord> findTenantById(Long tenantId) {
        return Optional.ofNullable(tenantMapper.selectById(tenantId)).map(this::toTenantRecord);
    }

    @Override
    public Optional<UserRecord> findUserByUsername(Long tenantId, String username) {
        return Optional.ofNullable(userMapper.selectOne(Wrappers.<IamUserPO>lambdaQuery()
                        .eq(IamUserPO::getTenantId, tenantId)
                        .eq(IamUserPO::getUsername, username)))
                .map(this::toUserRecord);
    }

    @Override
    public Optional<UserRecord> findUserById(Long tenantId, Long userId) {
        return Optional.ofNullable(userMapper.selectOne(Wrappers.<IamUserPO>lambdaQuery()
                        .eq(IamUserPO::getTenantId, tenantId)
                        .eq(IamUserPO::getId, userId)))
                .map(this::toUserRecord);
    }

    @Override
    public Optional<CredentialRecord> findCredential(Long tenantId, Long userId) {
        return Optional.ofNullable(credentialMapper.selectOne(Wrappers.<IamUserCredentialPO>lambdaQuery()
                        .eq(IamUserCredentialPO::getTenantId, tenantId)
                        .eq(IamUserCredentialPO::getUserId, userId)))
                .map(po -> new CredentialRecord(po.getId(), po.getTenantId(), po.getUserId(), po.getPasswordHash()));
    }

    @Override
    public List<RoleRecord> findRolesByUserId(Long tenantId, Long userId) {
        List<Long> roleIds = userRoleMapper
                .selectList(Wrappers.<IamUserRolePO>lambdaQuery()
                        .eq(IamUserRolePO::getTenantId, tenantId)
                        .eq(IamUserRolePO::getUserId, userId))
                .stream()
                .map(IamUserRolePO::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper
                .selectList(Wrappers.<IamRolePO>lambdaQuery()
                        .eq(IamRolePO::getTenantId, tenantId)
                        .in(IamRolePO::getId, roleIds)
                        .eq(IamRolePO::getStatus, STATUS_ENABLED))
                .stream()
                .map(this::toRoleRecord)
                .toList();
    }

    @Override
    public List<PermissionRecord> findPermissionsByUserId(Long tenantId, Long userId) {
        List<Long> roleIds = findRolesByUserId(tenantId, userId).stream().map(RoleRecord::id).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper
                .selectList(Wrappers.<IamRolePermissionPO>lambdaQuery()
                        .eq(IamRolePermissionPO::getTenantId, tenantId)
                        .in(IamRolePermissionPO::getRoleId, roleIds))
                .stream()
                .map(IamRolePermissionPO::getPermissionId)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper
                .selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .in(IamPermissionPO::getId, permissionIds)
                        .eq(IamPermissionPO::getStatus, STATUS_ENABLED)
                        .orderByAsc(IamPermissionPO::getPermissionCode))
                .stream()
                .map(this::toPermissionRecord)
                .toList();
    }

    @Override
    public List<MenuRecord> findMenusByUserId(Long tenantId, Long userId) {
        List<Long> roleIds = findRolesByUserId(tenantId, userId).stream().map(RoleRecord::id).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper
                .selectList(Wrappers.<IamRoleMenuPO>lambdaQuery()
                        .eq(IamRoleMenuPO::getTenantId, tenantId)
                        .in(IamRoleMenuPO::getRoleId, roleIds))
                .stream()
                .map(IamRoleMenuPO::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper
                .selectList(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .in(IamMenuPO::getId, menuIds)
                        .eq(IamMenuPO::getStatus, STATUS_ENABLED)
                        .orderByAsc(IamMenuPO::getSortOrder))
                .stream()
                .map(this::toMenuRecord)
                .toList();
    }

    @Override
    public List<TenantRecord> listTenants() {
        return tenantMapper.selectList(Wrappers.<PlatformTenantPO>lambdaQuery().orderByAsc(PlatformTenantPO::getTenantCode))
                .stream()
                .map(this::toTenantRecord)
                .toList();
    }

    @Override
    public Optional<TenantRecord> updateTenant(Long tenantId, String tenantName, String status) {
        PlatformTenantPO tenantPO = tenantMapper.selectById(tenantId);
        if (tenantPO == null) {
            return Optional.empty();
        }
        tenantPO.setTenantName(tenantName);
        tenantPO.setStatus(status);
        tenantPO.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenantPO);
        return Optional.of(toTenantRecord(tenantPO));
    }

    @Override
    public Optional<TenantRecord> updateTenantStatus(Long tenantId, String status) {
        PlatformTenantPO tenantPO = tenantMapper.selectById(tenantId);
        if (tenantPO == null) {
            return Optional.empty();
        }
        tenantPO.setStatus(status);
        tenantPO.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenantPO);
        return Optional.of(toTenantRecord(tenantPO));
    }

    @Override
    public List<RoleRecord> listRoles(Long tenantId) {
        return roleMapper
                .selectList(Wrappers.<IamRolePO>lambdaQuery()
                        .eq(IamRolePO::getTenantId, tenantId)
                        .orderByAsc(IamRolePO::getRoleCode))
                .stream()
                .map(this::toRoleRecord)
                .toList();
    }

    @Override
    public List<PermissionRecord> listPermissions(Long tenantId) {
        return permissionMapper
                .selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .orderByAsc(IamPermissionPO::getPermissionCode))
                .stream()
                .map(this::toPermissionRecord)
                .toList();
    }

    @Override
    public List<MenuRecord> listMenus(Long tenantId) {
        return menuMapper
                .selectList(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .orderByAsc(IamMenuPO::getSortOrder))
                .stream()
                .map(this::toMenuRecord)
                .toList();
    }

    @Override
    public List<PermissionRecord> findPermissionsByRoleId(Long tenantId, Long roleId) {
        List<Long> permissionIds = rolePermissionMapper
                .selectList(Wrappers.<IamRolePermissionPO>lambdaQuery()
                        .eq(IamRolePermissionPO::getTenantId, tenantId)
                        .eq(IamRolePermissionPO::getRoleId, roleId))
                .stream()
                .map(IamRolePermissionPO::getPermissionId)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper
                .selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .in(IamPermissionPO::getId, permissionIds)
                        .orderByAsc(IamPermissionPO::getPermissionCode))
                .stream()
                .map(this::toPermissionRecord)
                .toList();
    }

    @Override
    public List<MenuRecord> findMenusByRoleId(Long tenantId, Long roleId) {
        List<Long> menuIds = roleMenuMapper
                .selectList(Wrappers.<IamRoleMenuPO>lambdaQuery()
                        .eq(IamRoleMenuPO::getTenantId, tenantId)
                        .eq(IamRoleMenuPO::getRoleId, roleId))
                .stream()
                .map(IamRoleMenuPO::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper
                .selectList(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .in(IamMenuPO::getId, menuIds)
                        .orderByAsc(IamMenuPO::getSortOrder))
                .stream()
                .map(this::toMenuRecord)
                .toList();
    }

    @Override
    public Optional<RoleRecord> saveRole(Long tenantId, Long roleId, String roleCode, String roleName, String status) {
        LocalDateTime now = LocalDateTime.now();
        IamRolePO role = roleId == null
                ? roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                        .eq(IamRolePO::getTenantId, tenantId)
                        .eq(IamRolePO::getRoleCode, roleCode))
                : roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                        .eq(IamRolePO::getTenantId, tenantId)
                        .eq(IamRolePO::getId, roleId));
        if (roleId != null && role == null) {
            return Optional.empty();
        }
        IamRolePO sameCodeRole = roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                .eq(IamRolePO::getTenantId, tenantId)
                .eq(IamRolePO::getRoleCode, roleCode));
        if (sameCodeRole != null && (roleId == null || !sameCodeRole.getId().equals(roleId))) {
            return Optional.empty();
        }
        if (role == null) {
            role = new IamRolePO();
            role.setId(SnowflakeIdGenerator.nextId());
            role.setTenantId(tenantId);
            role.setCreatedAt(now);
        }
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setStatus(status);
        role.setUpdatedAt(now);
        if (roleMapper.selectById(role.getId()) == null) {
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
        return Optional.of(toRoleRecord(role));
    }

    @Override
    public Optional<PermissionRecord> savePermission(
            Long tenantId, Long permissionId, String permissionCode, String permissionName, String status) {
        LocalDateTime now = LocalDateTime.now();
        IamPermissionPO permission = permissionId == null
                ? permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .eq(IamPermissionPO::getPermissionCode, permissionCode))
                : permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                        .eq(IamPermissionPO::getTenantId, tenantId)
                        .eq(IamPermissionPO::getId, permissionId));
        if (permissionId != null && permission == null) {
            return Optional.empty();
        }
        IamPermissionPO sameCodePermission = permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .eq(IamPermissionPO::getPermissionCode, permissionCode));
        if (sameCodePermission != null && (permissionId == null || !sameCodePermission.getId().equals(permissionId))) {
            return Optional.empty();
        }
        if (permission == null) {
            permission = new IamPermissionPO();
            permission.setId(SnowflakeIdGenerator.nextId());
            permission.setTenantId(tenantId);
            permission.setCreatedAt(now);
        }
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(permissionName);
        permission.setStatus(status);
        permission.setUpdatedAt(now);
        if (permissionMapper.selectById(permission.getId()) == null) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }
        return Optional.of(toPermissionRecord(permission));
    }

    @Override
    public Optional<MenuRecord> saveMenu(
            Long tenantId,
            Long menuId,
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder,
            String status) {
        if (parentCode != null && parentCode.equals(menuCode)) {
            return Optional.empty();
        }
        if (parentCode != null
                && menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                                .eq(IamMenuPO::getTenantId, tenantId)
                                .eq(IamMenuPO::getMenuCode, parentCode))
                        == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        IamMenuPO menu = menuId == null
                ? menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .eq(IamMenuPO::getMenuCode, menuCode))
                : menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                        .eq(IamMenuPO::getTenantId, tenantId)
                        .eq(IamMenuPO::getId, menuId));
        if (menuId != null && menu == null) {
            return Optional.empty();
        }
        IamMenuPO sameCodeMenu = menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .eq(IamMenuPO::getMenuCode, menuCode));
        if (sameCodeMenu != null && (menuId == null || !sameCodeMenu.getId().equals(menuId))) {
            return Optional.empty();
        }
        if (menu == null) {
            menu = new IamMenuPO();
            menu.setId(SnowflakeIdGenerator.nextId());
            menu.setTenantId(tenantId);
            menu.setCreatedAt(now);
        }
        menu.setMenuCode(menuCode);
        menu.setMenuName(menuName);
        menu.setParentCode(parentCode);
        menu.setRoutePath(routePath);
        menu.setComponentKey(componentKey);
        menu.setPlatformScope(platformScope);
        menu.setSortOrder(sortOrder);
        menu.setStatus(status);
        menu.setUpdatedAt(now);
        if (menuMapper.selectById(menu.getId()) == null) {
            menuMapper.insert(menu);
        } else {
            menuMapper.updateById(menu);
        }
        return Optional.of(toMenuRecord(menu));
    }

    @Override
    public Optional<RoleAccessRecord> updateRoleBindings(
            Long tenantId, Long roleId, List<String> permissionCodes, List<String> menuCodes) {
        IamRolePO role = roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                .eq(IamRolePO::getTenantId, tenantId)
                .eq(IamRolePO::getId, roleId));
        if (role == null) {
            return Optional.empty();
        }
        Set<String> uniquePermissionCodes = Set.copyOf(permissionCodes);
        Set<String> uniqueMenuCodes = Set.copyOf(menuCodes);
        List<IamPermissionPO> permissions = permissionMapper.selectList(Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .in(IamPermissionPO::getPermissionCode, uniquePermissionCodes)
                .orderByAsc(IamPermissionPO::getPermissionCode));
        List<IamMenuPO> menus = menuMapper.selectList(Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .in(IamMenuPO::getMenuCode, uniqueMenuCodes)
                .orderByAsc(IamMenuPO::getSortOrder));
        if (permissions.size() != uniquePermissionCodes.size() || menus.size() != uniqueMenuCodes.size()) {
            return Optional.empty();
        }

        rolePermissionMapper.delete(Wrappers.<IamRolePermissionPO>lambdaQuery()
                .eq(IamRolePermissionPO::getTenantId, tenantId)
                .eq(IamRolePermissionPO::getRoleId, roleId));
        roleMenuMapper.delete(Wrappers.<IamRoleMenuPO>lambdaQuery()
                .eq(IamRoleMenuPO::getTenantId, tenantId)
                .eq(IamRoleMenuPO::getRoleId, roleId));

        LocalDateTime now = LocalDateTime.now();
        for (IamPermissionPO permission : permissions) {
            upsertRolePermission(tenantId, roleId, permission.getId(), now);
        }
        for (IamMenuPO menu : menus) {
            upsertRoleMenu(tenantId, roleId, menu.getId(), now);
        }
        return Optional.of(new RoleAccessRecord(
                toRoleRecord(role),
                permissions.stream().map(this::toPermissionRecord).toList(),
                menus.stream().map(this::toMenuRecord).toList()));
    }

    @Override
    public void bootstrapTenantUser(BootstrapTenant tenant, BootstrapUser user, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();
        PlatformTenantPO tenantPO = tenantMapper.selectOne(Wrappers.<PlatformTenantPO>lambdaQuery()
                .eq(PlatformTenantPO::getTenantCode, tenant.tenantCode()));
        if (tenantPO == null) {
            tenantPO = new PlatformTenantPO();
            tenantPO.setId(SnowflakeIdGenerator.nextId());
            tenantPO.setTenantCode(tenant.tenantCode());
            tenantPO.setCreatedAt(now);
        }
        tenantPO.setTenantName(tenant.tenantName());
        tenantPO.setStatus(STATUS_ENABLED);
        tenantPO.setUpdatedAt(now);
        if (tenantMapper.selectById(tenantPO.getId()) == null) {
            tenantMapper.insert(tenantPO);
        } else {
            tenantMapper.updateById(tenantPO);
        }

        Long tenantId = tenantPO.getId();
        TenantContext.runWith(new TenantId(tenantId), () -> upsertUserAndCredential(tenantId, user, passwordHash, now));
    }

    @Override
    public void appendAudit(Long tenantId, Long actorId, String action, String result, String detail) {
        AuditLogPO auditLogPO = new AuditLogPO();
        auditLogPO.setId(SnowflakeIdGenerator.nextId());
        auditLogPO.setTenantId(tenantId);
        auditLogPO.setActorId(actorId);
        auditLogPO.setAction(action);
        auditLogPO.setResult(result);
        auditLogPO.setDetail(detail);
        auditLogPO.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(auditLogPO);
    }

    private void upsertUserAndCredential(Long tenantId, BootstrapUser user, String passwordHash, LocalDateTime now) {
        IamUserPO userPO = userMapper.selectOne(Wrappers.<IamUserPO>lambdaQuery()
                .eq(IamUserPO::getTenantId, tenantId)
                .eq(IamUserPO::getUsername, user.username()));
        if (userPO == null) {
            userPO = new IamUserPO();
            userPO.setId(SnowflakeIdGenerator.nextId());
            userPO.setTenantId(tenantId);
            userPO.setUsername(user.username());
            userPO.setCreatedAt(now);
        }
        userPO.setDisplayName(user.displayName());
        userPO.setStatus(STATUS_ENABLED);
        userPO.setUpdatedAt(now);
        if (userMapper.selectById(userPO.getId()) == null) {
            userMapper.insert(userPO);
        } else {
            userMapper.updateById(userPO);
        }

        IamUserCredentialPO credentialPO = credentialMapper.selectOne(Wrappers.<IamUserCredentialPO>lambdaQuery()
                .eq(IamUserCredentialPO::getTenantId, tenantId)
                .eq(IamUserCredentialPO::getUserId, userPO.getId()));
        if (credentialPO == null) {
            credentialPO = new IamUserCredentialPO();
            credentialPO.setId(SnowflakeIdGenerator.nextId());
            credentialPO.setTenantId(tenantId);
            credentialPO.setUserId(userPO.getId());
        }
        credentialPO.setPasswordHash(passwordHash);
        credentialPO.setUpdatedAt(now);
        if (credentialMapper.selectById(credentialPO.getId()) == null) {
            credentialMapper.insert(credentialPO);
        } else {
            credentialMapper.updateById(credentialPO);
        }
        bootstrapAccessControl(tenantId, userPO.getId(), now);
    }

    private void bootstrapAccessControl(Long tenantId, Long userId, LocalDateTime now) {
        IamRolePO role = upsertRole(tenantId, "PLATFORM_ADMIN", "平台管理员", now);
        for (DefaultPermission permission : DEFAULT_PERMISSIONS) {
            IamPermissionPO permissionPO =
                    upsertPermission(tenantId, permission.permissionCode(), permission.permissionName(), now);
            upsertRolePermission(tenantId, role.getId(), permissionPO.getId(), now);
        }
        for (DefaultMenu menu : DEFAULT_MENUS) {
            IamMenuPO menuPO = upsertMenu(tenantId, menu, now);
            upsertRoleMenu(tenantId, role.getId(), menuPO.getId(), now);
        }
        retireMenus(tenantId, now);
        upsertUserRole(tenantId, userId, role.getId(), now);
    }

    private void retireMenus(Long tenantId, LocalDateTime now) {
        for (String menuCode : RETIRED_MENU_CODES) {
            IamMenuPO menu = menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                    .eq(IamMenuPO::getTenantId, tenantId)
                    .eq(IamMenuPO::getMenuCode, menuCode));
            if (menu == null || !STATUS_ENABLED.equals(menu.getStatus())) {
                continue;
            }
            menu.setStatus("DISABLED");
            menu.setUpdatedAt(now);
            menuMapper.updateById(menu);
        }
    }

    private IamRolePO upsertRole(Long tenantId, String roleCode, String roleName, LocalDateTime now) {
        IamRolePO po = roleMapper.selectOne(Wrappers.<IamRolePO>lambdaQuery()
                .eq(IamRolePO::getTenantId, tenantId)
                .eq(IamRolePO::getRoleCode, roleCode));
        if (po == null) {
            po = new IamRolePO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setTenantId(tenantId);
            po.setRoleCode(roleCode);
            po.setCreatedAt(now);
        }
        po.setRoleName(roleName);
        po.setStatus(STATUS_ENABLED);
        po.setUpdatedAt(now);
        if (roleMapper.selectById(po.getId()) == null) {
            roleMapper.insert(po);
        } else {
            roleMapper.updateById(po);
        }
        return po;
    }

    private IamPermissionPO upsertPermission(Long tenantId, String permissionCode, String permissionName, LocalDateTime now) {
        IamPermissionPO po = permissionMapper.selectOne(Wrappers.<IamPermissionPO>lambdaQuery()
                .eq(IamPermissionPO::getTenantId, tenantId)
                .eq(IamPermissionPO::getPermissionCode, permissionCode));
        if (po == null) {
            po = new IamPermissionPO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setTenantId(tenantId);
            po.setPermissionCode(permissionCode);
            po.setCreatedAt(now);
        }
        po.setPermissionName(permissionName);
        po.setStatus(STATUS_ENABLED);
        po.setUpdatedAt(now);
        if (permissionMapper.selectById(po.getId()) == null) {
            permissionMapper.insert(po);
        } else {
            permissionMapper.updateById(po);
        }
        return po;
    }

    private IamMenuPO upsertMenu(Long tenantId, DefaultMenu menu, LocalDateTime now) {
        IamMenuPO po = menuMapper.selectOne(Wrappers.<IamMenuPO>lambdaQuery()
                .eq(IamMenuPO::getTenantId, tenantId)
                .eq(IamMenuPO::getMenuCode, menu.menuCode()));
        if (po == null) {
            po = new IamMenuPO();
            po.setId(SnowflakeIdGenerator.nextId());
            po.setTenantId(tenantId);
            po.setMenuCode(menu.menuCode());
            po.setCreatedAt(now);
        }
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

    private void upsertUserRole(Long tenantId, Long userId, Long roleId, LocalDateTime now) {
        if (userRoleMapper.selectOne(Wrappers.<IamUserRolePO>lambdaQuery()
                        .eq(IamUserRolePO::getTenantId, tenantId)
                        .eq(IamUserRolePO::getUserId, userId)
                        .eq(IamUserRolePO::getRoleId, roleId))
                != null) {
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
                        .eq(IamRolePermissionPO::getPermissionId, permissionId))
                != null) {
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
                        .eq(IamRoleMenuPO::getMenuId, menuId))
                != null) {
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

    private TenantRecord toTenantRecord(PlatformTenantPO po) {
        return new TenantRecord(po.getId(), po.getTenantCode(), po.getTenantName(), po.getStatus());
    }

    private UserRecord toUserRecord(IamUserPO po) {
        return new UserRecord(po.getId(), po.getTenantId(), po.getUsername(), po.getDisplayName(), po.getStatus());
    }

    private RoleRecord toRoleRecord(IamRolePO po) {
        return new RoleRecord(po.getId(), po.getTenantId(), po.getRoleCode(), po.getRoleName(), po.getStatus());
    }

    private PermissionRecord toPermissionRecord(IamPermissionPO po) {
        return new PermissionRecord(
                po.getId(), po.getTenantId(), po.getPermissionCode(), po.getPermissionName(), po.getStatus());
    }

    private MenuRecord toMenuRecord(IamMenuPO po) {
        return new MenuRecord(
                po.getId(),
                po.getTenantId(),
                po.getMenuCode(),
                po.getMenuName(),
                po.getParentCode(),
                po.getRoutePath(),
                po.getComponentKey(),
                po.getPlatformScope(),
                po.getSortOrder(),
                po.getStatus());
    }

    private record DefaultPermission(String permissionCode, String permissionName) {
    }

    private record DefaultMenu(
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder) {
    }
}
