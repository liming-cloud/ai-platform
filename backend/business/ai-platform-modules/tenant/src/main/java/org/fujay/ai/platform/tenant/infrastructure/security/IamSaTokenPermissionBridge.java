package org.fujay.ai.platform.tenant.infrastructure.security;

import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import org.fujay.ai.platform.security.AuthPrincipal;
import org.fujay.ai.platform.security.AuthPrincipalCodec;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.tenant.domain.repository.TenantAccessRepository;
import org.springframework.stereotype.Component;

@Component
public class IamSaTokenPermissionBridge implements StpInterface {

    private final AuthPrincipalCodec principalCodec;
    private final TenantAccessRepository repository;

    public IamSaTokenPermissionBridge(AuthPrincipalCodec principalCodec, TenantAccessRepository repository) {
        this.principalCodec = principalCodec;
        this.repository = repository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        AuthPrincipal principal = principalCodec.decode(String.valueOf(loginId));
        return TenantContext.callWith(principal.tenantId(), () -> repository
                .findPermissionsByUserId(principal.tenantId().value(), principal.actorId())
                .stream()
                .map(TenantAccessRepository.PermissionRecord::permissionCode)
                .toList());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AuthPrincipal principal = principalCodec.decode(String.valueOf(loginId));
        return TenantContext.callWith(principal.tenantId(), () -> repository
                .findRolesByUserId(principal.tenantId().value(), principal.actorId())
                .stream()
                .map(TenantAccessRepository.RoleRecord::roleCode)
                .toList());
    }
}
