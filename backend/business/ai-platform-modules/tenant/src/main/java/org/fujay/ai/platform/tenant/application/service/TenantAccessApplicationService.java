package org.fujay.ai.platform.tenant.application.service;

import java.util.List;
import java.util.Objects;
import org.fujay.ai.platform.security.AuthPrincipal;
import org.fujay.ai.platform.security.LoginToken;
import org.fujay.ai.platform.security.PasswordHasher;
import org.fujay.ai.platform.security.SecuritySessionFacade;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.tenant.application.command.CreateTenantCommand;
import org.fujay.ai.platform.tenant.application.command.LoginCommand;
import org.fujay.ai.platform.tenant.application.command.SaveMenuCommand;
import org.fujay.ai.platform.tenant.application.command.SavePermissionCommand;
import org.fujay.ai.platform.tenant.application.command.SaveRoleCommand;
import org.fujay.ai.platform.tenant.application.command.UpdateTenantCommand;
import org.fujay.ai.platform.tenant.application.dto.AuthenticatedSession;
import org.fujay.ai.platform.tenant.application.dto.CurrentUserProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.MenuProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.PermissionProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleManagementProfile;
import org.fujay.ai.platform.tenant.application.dto.TenantProfile;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.MenuRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.PermissionRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.RoleAccessRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.RoleRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.TenantRecord;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository.UserRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAccessApplicationService {

    private static final String STATUS_ENABLED = "ENABLED";

    private final TenantAccessRepository repository;
    private final PasswordHasher passwordHasher;
    private final SecuritySessionFacade sessionFacade;

    public TenantAccessApplicationService(
            TenantAccessRepository repository,
            PasswordHasher passwordHasher,
            SecuritySessionFacade sessionFacade) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.sessionFacade = sessionFacade;
    }

    @Transactional
    public AuthenticatedSession login(LoginCommand command) {
        requireNotBlank(command.tenantCode(), "租户编码不能为空");
        requireNotBlank(command.username(), "用户名不能为空");
        requireNotBlank(command.password(), "密码不能为空");

        TenantRecord tenant = repository
                .findTenantByCode(command.tenantCode())
                .filter(this::isEnabled)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));

        TenantId tenantId = new TenantId(tenant.id());
        return TenantContext.callWith(tenantId, () -> doLogin(command, tenant));
    }

    @Transactional(readOnly = true)
    public CurrentUserProfile currentUser() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> {
            UserRecord user = repository
                    .findUserById(principal.tenantId().value(), principal.actorId())
                    .filter(this::isEnabled)
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.UNAUTHORIZED));
            TenantRecord tenant = repository
                    .findTenantById(principal.tenantId().value())
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.FORBIDDEN));
            return new CurrentUserProfile(
                    tenant.id(),
                    tenant.tenantCode(),
                    user.id(),
                    user.username(),
                    user.displayName(),
                    repository.findRolesByUserId(tenant.id(), user.id()).stream()
                            .map(role -> new CurrentUserProfile.RoleProfile(role.id(), role.roleCode(), role.roleName()))
                            .toList(),
                    repository.findPermissionsByUserId(tenant.id(), user.id()).stream()
                            .map(permission -> new CurrentUserProfile.PermissionProfile(
                                    permission.id(), permission.permissionCode(), permission.permissionName()))
                            .toList(),
                    repository.findMenusByUserId(tenant.id(), user.id()).stream()
                            .map(menu -> new CurrentUserProfile.MenuProfile(
                                    menu.id(),
                                    menu.menuCode(),
                                    menu.menuName(),
                                    menu.parentCode(),
                                    menu.routePath(),
                                    menu.componentKey(),
                                    menu.platformScope(),
                                    menu.sortOrder()))
                            .toList());
        });
    }

    @Transactional(readOnly = true)
    public TenantProfile currentTenant() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> toTenantProfile(repository
                .findTenantById(principal.tenantId().value())
                .orElseThrow(() -> new PlatformException(CommonErrorCode.FORBIDDEN))));
    }

    @Transactional
    public TenantProfile tenantById(String tenantId) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        Long requestedTenantId = parseId(tenantId);
        if (!principal.tenantId().value().equals(requestedTenantId)) {
            TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                    principal.tenantId().value(),
                    principal.actorId(),
                    "TENANT_READ",
                    "DENIED",
                    "跨租户访问被拒绝"));
            throw new PlatformException(CommonErrorCode.FORBIDDEN);
        }
        return currentTenant();
    }

    @Transactional(readOnly = true)
    public List<TenantProfile> listTenantsForAdmin() {
        sessionFacade.currentPrincipal();
        return repository.listTenants().stream().map(this::toTenantProfile).toList();
    }

    @Transactional
    public TenantProfile createTenantForAdmin(CreateTenantCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        String tenantCode = normalizeRequired(command.tenantCode(), "租户编码不能为空");
        String tenantName = normalizeRequired(command.tenantName(), "租户名称不能为空");
        String adminUsername = normalizeRequired(command.adminUsername(), "管理员用户名不能为空");
        String adminDisplayName = normalizeRequired(command.adminDisplayName(), "管理员名称不能为空");
        String adminPassword = normalizeRequired(command.adminPassword(), "管理员密码不能为空");
        String normalizedStatus = normalizeStatus(defaultStatus(command.status()));
        if (repository.findTenantByCode(tenantCode).isPresent()) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }

        repository.bootstrapTenantUser(
                new TenantAccessRepository.BootstrapTenant(tenantCode, tenantName),
                new TenantAccessRepository.BootstrapUser(adminUsername, adminDisplayName),
                passwordHasher.hash(adminPassword));
        TenantRecord tenant = repository
                .findTenantByCode(tenantCode)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        if (!STATUS_ENABLED.equals(normalizedStatus)) {
            tenant = repository
                    .updateTenantStatus(tenant.id(), normalizedStatus)
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        }
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(),
                principal.actorId(),
                "TENANT_CREATE",
                "SUCCESS",
                "租户 " + tenantCode + " 已创建"));
        return toTenantProfile(tenant);
    }

    @Transactional
    public TenantProfile updateTenantForAdmin(String tenantId, UpdateTenantCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        String tenantName = normalizeRequired(command.tenantName(), "租户名称不能为空");
        String normalizedStatus = normalizeStatus(defaultStatus(command.status()));
        TenantProfile profile = toTenantProfile(repository
                .updateTenant(parseId(tenantId), tenantName, normalizedStatus)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST)));
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(),
                principal.actorId(),
                "TENANT_UPDATE",
                "SUCCESS",
                "租户基础信息已更新"));
        return profile;
    }

    @Transactional
    public TenantProfile updateTenantStatusForAdmin(String tenantId, String status) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        String normalizedStatus = normalizeStatus(status);
        TenantProfile profile = toTenantProfile(repository
                .updateTenantStatus(parseId(tenantId), normalizedStatus)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST)));
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(),
                principal.actorId(),
                "TENANT_STATUS_UPDATE",
                "SUCCESS",
                "租户状态更新为 " + normalizedStatus));
        return profile;
    }

    @Transactional(readOnly = true)
    public List<PermissionProfile> listPermissionsForAdmin() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> repository.listPermissions(principal.tenantId().value()).stream()
                .map(this::toPermissionProfile)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<MenuProfile> listMenusForAdmin() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> repository.listMenus(principal.tenantId().value()).stream()
                .map(this::toMenuProfile)
                .toList());
    }

    @Transactional(readOnly = true)
    public RoleManagementProfile roleManagementForAdmin() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> new RoleManagementProfile(
                repository.listRoles(principal.tenantId().value()).stream()
                        .map(role -> new RoleAccessProfile(
                                role.id(),
                                role.roleCode(),
                                role.roleName(),
                                role.status(),
                                repository.findPermissionsByRoleId(principal.tenantId().value(), role.id()).stream()
                                        .map(this::toPermissionProfile)
                                        .toList(),
                                repository.findMenusByRoleId(principal.tenantId().value(), role.id()).stream()
                                        .map(this::toMenuProfile)
                                        .toList()))
                        .toList(),
                repository.listPermissions(principal.tenantId().value()).stream()
                        .map(this::toPermissionProfile)
                        .toList(),
                repository.listMenus(principal.tenantId().value()).stream().map(this::toMenuProfile).toList()));
    }

    @Transactional
    public RoleAccessProfile updateRoleBindingsForAdmin(
            String roleId, List<String> permissionCodes, List<String> menuCodes) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        List<String> normalizedPermissionCodes = normalizeCodes(permissionCodes, "角色权限不能为空");
        List<String> normalizedMenuCodes = normalizeCodes(menuCodes, "角色菜单不能为空");
        RoleAccessRecord roleAccess = TenantContext.callWith(
                principal.tenantId(),
                () -> repository
                        .updateRoleBindings(
                                principal.tenantId().value(), parseId(roleId), normalizedPermissionCodes, normalizedMenuCodes)
                        .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST)));
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(),
                principal.actorId(),
                "ROLE_BINDING_UPDATE",
                "SUCCESS",
                "角色权限和菜单配置已更新"));
        return toRoleAccessProfile(roleAccess);
    }

    @Transactional
    public RoleAccessProfile saveRoleForAdmin(SaveRoleCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        String roleCode = normalizeRequired(command.roleCode(), "角色编码不能为空");
        String roleName = normalizeRequired(command.roleName(), "角色名称不能为空");
        String normalizedStatus = normalizeStatus(defaultStatus(command.status()));
        RoleRecord role = TenantContext.callWith(principal.tenantId(), () -> repository
                .saveRole(principal.tenantId().value(), parseOptionalId(command.roleId()), roleCode, roleName, normalizedStatus)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST)));
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(), principal.actorId(), "ROLE_SAVE", "SUCCESS", "角色已保存"));
        return TenantContext.callWith(principal.tenantId(), () -> toRoleAccessProfile(role));
    }

    @Transactional
    public PermissionProfile savePermissionForAdmin(SavePermissionCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        String permissionCode = normalizeRequired(command.permissionCode(), "权限编码不能为空");
        String permissionName = normalizeRequired(command.permissionName(), "权限名称不能为空");
        String normalizedStatus = normalizeStatus(defaultStatus(command.status()));
        PermissionRecord permission = TenantContext.callWith(principal.tenantId(), () -> repository
                .savePermission(
                        principal.tenantId().value(),
                        parseOptionalId(command.permissionId()),
                        permissionCode,
                        permissionName,
                        normalizedStatus)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST)));
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(), principal.actorId(), "PERMISSION_SAVE", "SUCCESS", "权限点已保存"));
        return toPermissionProfile(permission);
    }

    @Transactional
    public MenuProfile saveMenuForAdmin(SaveMenuCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        String menuCode = normalizeRequired(command.menuCode(), "菜单编码不能为空");
        String menuName = normalizeRequired(command.menuName(), "菜单名称不能为空");
        String routePath = normalizeRequired(command.routePath(), "菜单路由不能为空");
        String componentKey = normalizeRequired(command.componentKey(), "菜单组件不能为空");
        String platformScope = normalizePlatformScope(command.platformScope());
        String normalizedStatus = normalizeStatus(defaultStatus(command.status()));
        Integer sortOrder = command.sortOrder() == null ? 0 : command.sortOrder();
        MenuRecord menu = TenantContext.callWith(principal.tenantId(), () -> repository
                .saveMenu(
                        principal.tenantId().value(),
                        parseOptionalId(command.menuId()),
                        menuCode,
                        menuName,
                        normalizeOptional(command.parentCode()),
                        routePath,
                        componentKey,
                        platformScope,
                        sortOrder,
                        normalizedStatus)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST)));
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(), principal.actorId(), "MENU_SAVE", "SUCCESS", "菜单已保存"));
        return toMenuProfile(menu);
    }

    @Transactional
    public void logout() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        TenantContext.runWith(principal.tenantId(), () -> repository.appendAudit(
                principal.tenantId().value(), principal.actorId(), "LOGOUT", "SUCCESS", "用户退出登录"));
        sessionFacade.logout();
    }

    private AuthenticatedSession doLogin(LoginCommand command, TenantRecord tenant) {
        UserRecord user = repository
                .findUserByUsername(tenant.id(), command.username())
                .filter(this::isEnabled)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        String passwordHash = repository
                .findCredential(tenant.id(), user.id())
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST))
                .passwordHash();
        if (!passwordHasher.matches(command.password(), passwordHash)) {
            repository.appendAudit(tenant.id(), user.id(), "LOGIN", "DENIED", "用户名或密码不正确");
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }

        LoginToken token = sessionFacade.login(new AuthPrincipal(new TenantId(tenant.id()), "USER", user.id()));
        repository.appendAudit(tenant.id(), user.id(), "LOGIN", "SUCCESS", "用户登录成功");
        return new AuthenticatedSession(
                token.tokenName(),
                token.tokenValue(),
                tenant.id(),
                tenant.tenantCode(),
                user.id(),
                user.username(),
                user.displayName());
    }

    private TenantProfile toTenantProfile(TenantRecord tenant) {
        return new TenantProfile(tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.status());
    }

    private RoleAccessProfile toRoleAccessProfile(RoleAccessRecord roleAccess) {
        RoleRecord role = roleAccess.role();
        return new RoleAccessProfile(
                role.id(),
                role.roleCode(),
                role.roleName(),
                role.status(),
                roleAccess.permissions().stream().map(this::toPermissionProfile).toList(),
                roleAccess.menus().stream().map(this::toMenuProfile).toList());
    }

    private RoleAccessProfile toRoleAccessProfile(RoleRecord role) {
        return new RoleAccessProfile(
                role.id(),
                role.roleCode(),
                role.roleName(),
                role.status(),
                repository.findPermissionsByRoleId(role.tenantId(), role.id()).stream()
                        .map(this::toPermissionProfile)
                        .toList(),
                repository.findMenusByRoleId(role.tenantId(), role.id()).stream().map(this::toMenuProfile).toList());
    }

    private PermissionProfile toPermissionProfile(PermissionRecord permission) {
        return new PermissionProfile(
                permission.id(), permission.permissionCode(), permission.permissionName(), permission.status());
    }

    private MenuProfile toMenuProfile(MenuRecord menu) {
        return new MenuProfile(
                menu.id(),
                menu.menuCode(),
                menu.menuName(),
                menu.parentCode(),
                menu.routePath(),
                menu.componentKey(),
                menu.platformScope(),
                menu.sortOrder(),
                menu.status());
    }

    private boolean isEnabled(TenantRecord tenant) {
        return STATUS_ENABLED.equals(tenant.status());
    }

    private boolean isEnabled(UserRecord user) {
        return STATUS_ENABLED.equals(user.status());
    }

    private void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeRequired(String value, String message) {
        requireNotBlank(value, message);
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
    }

    private Long parseOptionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseId(value);
    }

    private String normalizeStatus(String status) {
        requireNotBlank(status, "租户状态不能为空");
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
        return normalized;
    }

    private String defaultStatus(String status) {
        return status == null || status.isBlank() ? STATUS_ENABLED : status;
    }

    private String normalizePlatformScope(String platformScope) {
        String normalized = normalizeRequired(platformScope, "菜单范围不能为空").toUpperCase();
        if (!"PLATFORM".equals(normalized) && !"TENANT".equals(normalized)) {
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
        return normalized;
    }

    private List<String> normalizeCodes(List<String> codes, String emptyMessage) {
        if (codes == null) {
            throw new IllegalArgumentException(emptyMessage);
        }
        List<String> normalized = codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return normalized;
    }
}
