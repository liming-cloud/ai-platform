package org.fujay.ai.platform.tenant.application.service;

import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.tenant.application.command.ChangePasswordCommand;
import org.fujay.ai.platform.tenant.application.command.LoginCommand;
import org.fujay.ai.platform.tenant.application.dto.AuthenticatedSession;
import org.fujay.ai.platform.tenant.application.dto.CurrentUserProfile;
import org.fujay.ai.platform.tenant.application.dto.TenantProfile;
import org.fujay.ai.platform.tenant.application.port.CurrentTenantPrincipal;
import org.fujay.ai.platform.tenant.application.port.TenantLoginToken;
import org.fujay.ai.platform.tenant.application.port.TenantPasswordHashPort;
import org.fujay.ai.platform.tenant.application.port.TenantSecuritySessionPort;
import org.fujay.ai.platform.tenant.application.support.TenantProfileMapper;
import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.repository.IamMenuRepository;
import org.fujay.ai.platform.tenant.domain.repository.IamPermissionRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuditRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuthorizationRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantUserRepository;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAuthApplicationService {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository userRepository;
    private final TenantAuthorizationRepository authorizationRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamMenuRepository menuRepository;
    private final TenantAuditRepository auditRepository;
    private final TenantPasswordHashPort passwordHashPort;
    private final TenantSecuritySessionPort sessionPort;

    public TenantAuthApplicationService(
            TenantRepository tenantRepository,
            TenantUserRepository userRepository,
            TenantAuthorizationRepository authorizationRepository,
            IamPermissionRepository permissionRepository,
            IamMenuRepository menuRepository,
            TenantAuditRepository auditRepository,
            TenantPasswordHashPort passwordHashPort,
            TenantSecuritySessionPort sessionPort) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.authorizationRepository = authorizationRepository;
        this.permissionRepository = permissionRepository;
        this.menuRepository = menuRepository;
        this.auditRepository = auditRepository;
        this.passwordHashPort = passwordHashPort;
        this.sessionPort = sessionPort;
    }

    @Transactional
    public AuthenticatedSession login(LoginCommand command) {
        TenantApplicationAccessSpec.requireNotBlank(command.tenantCode(), "租户编码不能为空");
        TenantApplicationAccessSpec.requireNotBlank(command.username(), "用户名不能为空");
        TenantApplicationAccessSpec.requireNotBlank(command.password(), "密码不能为空");
        Tenant tenant = tenantRepository.findByCode(command.tenantCode())
                .filter(Tenant::enabled)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        return TenantContext.callWith(new TenantId(tenant.id()), () -> doLogin(command, tenant));
    }

    @Transactional(readOnly = true)
    public CurrentUserProfile currentUser() {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        return TenantContext.callWith(new TenantId(principal.tenantId()), () -> {
            TenantUser user = userRepository.findById(principal.tenantId(), principal.actorId())
                    .filter(TenantUser::enabled)
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.UNAUTHORIZED));
            Tenant tenant = tenantRepository.findById(principal.tenantId())
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.FORBIDDEN));
            return new CurrentUserProfile(
                    tenant.id(),
                    tenant.tenantCode(),
                    user.id(),
                    user.username(),
                    user.displayName(),
                    authorizationRepository.findRolesByUserId(tenant.id(), user.id()).stream()
                            .map(role -> new CurrentUserProfile.RoleProfile(role.id(), role.roleCode(), role.roleName()))
                            .toList(),
                    permissionsFor(user).stream()
                            .map(permission -> new CurrentUserProfile.PermissionProfile(
                                    permission.id(), permission.permissionCode(), permission.permissionName()))
                            .toList(),
                    menusFor(user).stream()
                            .map(menu -> new CurrentUserProfile.MenuProfile(
                                    menu.id(), menu.menuCode(), menu.menuName(), menu.parentCode(), menu.routePath(),
                                    menu.componentKey(), menu.platformScope(), menu.sortOrder()))
                            .toList());
        });
    }

    @Transactional(readOnly = true)
    public TenantProfile currentTenant() {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        return TenantContext.callWith(new TenantId(principal.tenantId()), () -> TenantProfileMapper.toTenantProfile(tenantRepository
                .findById(principal.tenantId())
                .orElseThrow(() -> new PlatformException(CommonErrorCode.FORBIDDEN))));
    }

    @Transactional
    public TenantProfile tenantById(String tenantId) {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        Long requestedTenantId = TenantApplicationAccessSpec.parseId(tenantId);
        if (!principal.tenantId().equals(requestedTenantId)) {
            TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                    principal.tenantId(), principal.actorId(), "TENANT_READ", "DENIED", "跨租户访问被拒绝"));
            throw new PlatformException(CommonErrorCode.FORBIDDEN);
        }
        return currentTenant();
    }

    @Transactional
    public void logout() {
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> auditRepository.append(
                principal.tenantId(), principal.actorId(), "LOGOUT", "SUCCESS", "用户退出登录"));
        sessionPort.logout();
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        TenantApplicationAccessSpec.requireNotBlank(command.oldPassword(), "旧密码不能为空");
        TenantApplicationAccessSpec.requireNotBlank(command.newPassword(), "新密码不能为空");
        CurrentTenantPrincipal principal = sessionPort.currentPrincipal();
        TenantContext.runWith(new TenantId(principal.tenantId()), () -> {
            TenantUser user = userRepository.findById(principal.tenantId(), principal.actorId())
                    .filter(TenantUser::enabled)
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.UNAUTHORIZED));
            user.ensurePasswordChangeAllowed();
            var credential = userRepository.findCredential(principal.tenantId(), user.id())
                    .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
            if (!passwordHashPort.matches(command.oldPassword(), credential.passwordHash())) {
                auditRepository.append(principal.tenantId(), user.id(), "PASSWORD_CHANGE", "DENIED", "旧密码不正确");
                throw new PlatformException(CommonErrorCode.BAD_REQUEST);
            }
            userRepository.saveCredential(credential.changePasswordHash(passwordHashPort.hash(command.newPassword())));
            auditRepository.append(principal.tenantId(), user.id(), "PASSWORD_CHANGE", "SUCCESS", "用户密码已修改");
        });
    }

    private AuthenticatedSession doLogin(LoginCommand command, Tenant tenant) {
        TenantUser user = userRepository.findByUsername(tenant.id(), command.username())
                .filter(TenantUser::enabled)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST));
        String passwordHash = userRepository.findCredential(tenant.id(), user.id())
                .orElseThrow(() -> new PlatformException(CommonErrorCode.BAD_REQUEST))
                .passwordHash();
        if (!passwordHashPort.matches(command.password(), passwordHash)) {
            auditRepository.append(tenant.id(), user.id(), "LOGIN", "DENIED", "用户名或密码不正确");
            throw new PlatformException(CommonErrorCode.BAD_REQUEST);
        }
        TenantLoginToken token = sessionPort.login(new CurrentTenantPrincipal(tenant.id(), "USER", user.id()));
        auditRepository.append(tenant.id(), user.id(), "LOGIN", "SUCCESS", "用户登录成功");
        return new AuthenticatedSession(
                token.tokenName(), token.tokenValue(), tenant.id(), tenant.tenantCode(), user.id(), user.username(), user.displayName());
    }

    private java.util.List<org.fujay.ai.platform.tenant.domain.model.IamPermission> permissionsFor(TenantUser user) {
        if (user.systemAccount()) {
            return permissionRepository.list(user.tenantId());
        }
        return authorizationRepository.findPermissionsByUserId(user.tenantId(), user.id());
    }

    private java.util.List<org.fujay.ai.platform.tenant.domain.model.IamMenu> menusFor(TenantUser user) {
        if (user.systemAccount()) {
            return menuRepository.list(user.tenantId());
        }
        return authorizationRepository.findMenusByUserId(user.tenantId(), user.id());
    }
}
