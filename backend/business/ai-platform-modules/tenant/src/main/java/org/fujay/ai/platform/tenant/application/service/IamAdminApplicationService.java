package org.fujay.ai.platform.tenant.application.service;

import java.util.List;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.tenant.application.command.SaveMenuCommand;
import org.fujay.ai.platform.tenant.application.command.SavePermissionCommand;
import org.fujay.ai.platform.tenant.application.command.SaveRoleCommand;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.MenuProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.PermissionProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleManagementProfile;
import org.fujay.ai.platform.tenant.application.port.CurrentTenantPrincipal;
import org.fujay.ai.platform.tenant.application.port.TenantIdGeneratorPort;
import org.fujay.ai.platform.tenant.application.port.TenantSecuritySessionPort;
import org.fujay.ai.platform.tenant.application.support.TenantProfileMapper;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.repository.IamMenuRepository;
import org.fujay.ai.platform.tenant.domain.repository.IamPermissionRepository;
import org.fujay.ai.platform.tenant.domain.repository.IamRoleRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuditRepository;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamAdminApplicationService {

    private final IamRoleRepository roleRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamMenuRepository menuRepository;
    private final TenantAuditRepository auditRepository;
    private final TenantSecuritySessionPort sessionPort;
    private final TenantIdGeneratorPort idGenerator;

    public IamAdminApplicationService(
            IamRoleRepository roleRepository,
            IamPermissionRepository permissionRepository,
            IamMenuRepository menuRepository,
            TenantAuditRepository auditRepository,
            TenantSecuritySessionPort sessionPort,
            TenantIdGeneratorPort idGenerator) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.menuRepository = menuRepository;
        this.auditRepository = auditRepository;
        this.sessionPort = sessionPort;
        this.idGenerator = idGenerator;
    }

    @Transactional(readOnly = true)
    public List<PermissionProfile> listPermissionsForAdmin() {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        return TenantContext.callWith(new TenantId(principal.tenantId()), () -> permissionRepository.list(principal.tenantId()).stream()
                .map(TenantProfileMapper::toPermissionProfile)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<MenuProfile> listMenusForAdmin() {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        return TenantContext.callWith(new TenantId(principal.tenantId()), () -> menuRepository.list(principal.tenantId()).stream()
                .map(TenantProfileMapper::toMenuProfile)
                .toList());
    }

    @Transactional(readOnly = true)
    public RoleManagementProfile roleManagementForAdmin() {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        return TenantContext.callWith(new TenantId(principal.tenantId()), () -> new RoleManagementProfile(
                roleRepository.list(principal.tenantId()).stream()
                        .map(this::toRoleAccessProfile)
                        .toList(),
                permissionRepository.list(principal.tenantId()).stream()
                        .map(TenantProfileMapper::toPermissionProfile)
                        .toList(),
                menuRepository.list(principal.tenantId()).stream()
                        .map(TenantProfileMapper::toMenuProfile)
                        .toList()));
    }

    @Transactional
    public RoleAccessProfile updateRoleBindingsForAdmin(
            String roleId, List<String> permissionCodes, List<String> menuCodes) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        List<String> normalizedPermissionCodes = TenantApplicationAccessSpec.normalizeCodes(permissionCodes, "角色权限不能为空");
        List<String> normalizedMenuCodes = TenantApplicationAccessSpec.normalizeCodes(menuCodes, "角色菜单不能为空");
        var roleAccess = TenantContext.callWith(new TenantId(principal.tenantId()), () -> {
            IamRole role = roleRepository.findById(principal.tenantId(), TenantApplicationAccessSpec.parseId(roleId))
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
            if (role.platformAdminRole()) {
                throw new PlatformException(CommonErrorCode.BAD_REQUEST);
            }
            List<IamPermission> permissions = permissionRepository.findByCodes(principal.tenantId(), normalizedPermissionCodes);
            List<IamMenu> menus = menuRepository.findByCodes(principal.tenantId(), normalizedMenuCodes);
            if (permissions.size() != normalizedPermissionCodes.size() || menus.size() != normalizedMenuCodes.size()) {
                throw new PlatformException(CommonErrorCode.BAD_REQUEST);
            }
            return roleRepository.replaceRoleAccess(role, permissions, menus);
        });
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "ROLE_BINDING_UPDATE", "SUCCESS", "角色权限和菜单配置已更新"));
        return TenantProfileMapper.toRoleAccessProfile(roleAccess);
    }

    @Transactional
    public RoleAccessProfile saveRoleForAdmin(SaveRoleCommand command) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        String roleCode = TenantApplicationAccessSpec.normalizeRequired(command.roleCode(), "角色编码不能为空");
        String roleName = TenantApplicationAccessSpec.normalizeRequired(command.roleName(), "角色名称不能为空");
        String status = TenantApplicationAccessSpec.normalizeStatus(TenantApplicationAccessSpec.defaultStatus(command.status()));
        var role = TenantContext.callWith(new TenantId(principal.tenantId()), () -> {
            Long roleId = TenantApplicationAccessSpec.parseOptionalId(command.roleId());
            IamRole existing = roleId == null
                    ? roleRepository.findByCode(principal.tenantId(), roleCode).orElse(null)
                    : roleRepository.findById(principal.tenantId(), roleId)
                            .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
            if (existing != null && existing.platformAdminRole()) {
                throw new PlatformException(CommonErrorCode.BAD_REQUEST);
            }
            IamRole roleToSave = existing == null
                    ? new IamRole(idGenerator.nextId(), principal.tenantId(), roleCode, roleName, status)
                    : existing.renameAndChangeStatus(roleName, status);
            return roleRepository.save(roleToSave).orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        });
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "ROLE_SAVE", "SUCCESS", "角色已保存"));
        return TenantContext.callWith(new TenantId(principal.tenantId()), () -> toRoleAccessProfile(role));
    }

    @Transactional
    public PermissionProfile savePermissionForAdmin(SavePermissionCommand command) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        String code = TenantApplicationAccessSpec.normalizeRequired(command.permissionCode(), "权限编码不能为空");
        String name = TenantApplicationAccessSpec.normalizeRequired(command.permissionName(), "权限名称不能为空");
        String status = TenantApplicationAccessSpec.normalizeStatus(TenantApplicationAccessSpec.defaultStatus(command.status()));
        var permission = TenantContext.callWith(new TenantId(principal.tenantId()), () -> {
            Long permissionId = TenantApplicationAccessSpec.parseOptionalId(command.permissionId());
            IamPermission existing = permissionId == null
                    ? permissionRepository.findByCode(principal.tenantId(), code).orElse(null)
                    : permissionRepository.list(principal.tenantId()).stream()
                            .filter(item -> item.id().equals(permissionId))
                            .findFirst()
                            .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
            IamPermission permissionToSave = existing == null
                    ? new IamPermission(idGenerator.nextId(), principal.tenantId(), code, name, status)
                    : existing.renameAndChangeStatus(name, status);
            return permissionRepository.save(permissionToSave)
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        });
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "PERMISSION_SAVE", "SUCCESS", "权限点已保存"));
        return TenantProfileMapper.toPermissionProfile(permission);
    }

    @Transactional
    public MenuProfile saveMenuForAdmin(SaveMenuCommand command) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        String menuCode = TenantApplicationAccessSpec.normalizeRequired(command.menuCode(), "菜单编码不能为空");
        String menuName = TenantApplicationAccessSpec.normalizeRequired(command.menuName(), "菜单名称不能为空");
        String routePath = TenantApplicationAccessSpec.normalizeRequired(command.routePath(), "菜单路由不能为空");
        String componentKey = TenantApplicationAccessSpec.normalizeRequired(command.componentKey(), "菜单组件不能为空");
        String platformScope = TenantApplicationAccessSpec.normalizePlatformScope(command.platformScope());
        String status = TenantApplicationAccessSpec.normalizeStatus(TenantApplicationAccessSpec.defaultStatus(command.status()));
        var menu = TenantContext.callWith(new TenantId(principal.tenantId()), () -> {
            Long menuId = TenantApplicationAccessSpec.parseOptionalId(command.menuId());
            IamMenu existing = menuId == null
                    ? menuRepository.findByCode(principal.tenantId(), menuCode).orElse(null)
                    : menuRepository.list(principal.tenantId()).stream()
                            .filter(item -> item.id().equals(menuId))
                            .findFirst()
                            .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
            IamMenu menuToSave = existing == null
                    ? new IamMenu(
                            idGenerator.nextId(),
                            principal.tenantId(),
                            menuCode,
                            menuName,
                            command.parentCode(),
                            routePath,
                            componentKey,
                            platformScope,
                            command.sortOrder(),
                            status)
                    : existing.changeDefinition(
                            menuName,
                            command.parentCode(),
                            routePath,
                            componentKey,
                            platformScope,
                            command.sortOrder(),
                            status);
            return menuRepository.save(menuToSave).orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        });
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "MENU_SAVE", "SUCCESS", "菜单已保存"));
        return TenantProfileMapper.toMenuProfile(menu);
    }

    private RoleAccessProfile toRoleAccessProfile(IamRole role) {
        return TenantProfileMapper.toRoleAccessProfile(new org.fujay.ai.platform.tenant.domain.model.RoleAccess(
                role,
                roleRepository.findPermissionsByRoleId(role.tenantId(), role.id()),
                roleRepository.findMenusByRoleId(role.tenantId(), role.id())));
    }
}
