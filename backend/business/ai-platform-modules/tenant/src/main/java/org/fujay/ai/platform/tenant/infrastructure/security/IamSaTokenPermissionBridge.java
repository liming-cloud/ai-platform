package org.fujay.ai.platform.tenant.infrastructure.security;

import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import org.fujay.ai.platform.security.AuthPrincipal;
import org.fujay.ai.platform.security.AuthPrincipalCodec;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.TenantUser;
import org.fujay.ai.platform.tenant.domain.repository.IamPermissionRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantAuthorizationRepository;
import org.fujay.ai.platform.tenant.domain.repository.TenantUserRepository;
import org.springframework.stereotype.Component;

@Component
public class IamSaTokenPermissionBridge implements StpInterface {

    private final AuthPrincipalCodec principalCodec;
    private final TenantUserRepository userRepository;
    private final TenantAuthorizationRepository authorizationRepository;
    private final IamPermissionRepository permissionRepository;

    public IamSaTokenPermissionBridge(
            AuthPrincipalCodec principalCodec,
            TenantUserRepository userRepository,
            TenantAuthorizationRepository authorizationRepository,
            IamPermissionRepository permissionRepository) {
        this.principalCodec = principalCodec;
        this.userRepository = userRepository;
        this.authorizationRepository = authorizationRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        AuthPrincipal principal = principalCodec.decode(String.valueOf(loginId));
        return TenantContext.callWith(principal.tenantId(), () -> {
            TenantUser user = userRepository.findById(principal.tenantId().value(), principal.actorId()).orElse(null);
            if (user != null && user.systemAccount()) {
                return permissionRepository.list(principal.tenantId().value()).stream()
                        .map(IamPermission::permissionCode)
                        .toList();
            }
            return authorizationRepository.findPermissionsByUserId(principal.tenantId().value(), principal.actorId()).stream()
                    .map(IamPermission::permissionCode)
                    .toList();
        });
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AuthPrincipal principal = principalCodec.decode(String.valueOf(loginId));
        return TenantContext.callWith(principal.tenantId(), () -> authorizationRepository
                .findRolesByUserId(principal.tenantId().value(), principal.actorId())
                .stream()
                .map(IamRole::roleCode)
                .toList());
    }
}
