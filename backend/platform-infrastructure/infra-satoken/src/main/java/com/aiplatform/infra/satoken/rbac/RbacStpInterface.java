package com.aiplatform.infra.satoken.rbac;

import cn.dev33.satoken.stp.StpInterface;
import com.aiplatform.infra.satoken.codec.SaTokenPrincipalCodec;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Sa-Token RBAC 桥接器，角色和权限均从 identity-access 权限目录查询；未登录返回空集合，损坏登录主体失败关闭。
 */
@Component
public class RbacStpInterface implements StpInterface {
    private final RbacAuthorizationQueryPort authorizationQueryPort;

    public RbacStpInterface(RbacAuthorizationQueryPort authorizationQueryPort) {
        this.authorizationQueryPort = Objects.requireNonNull(authorizationQueryPort, "RBAC 授权查询端口不能为空");
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return decode(loginId)
                .map(principal -> normalizedPermissionCodes(authorizationQueryPort.findPermissionCodes(
                        principal.tenantId(),
                        principal.actorType(),
                        principal.actorId()
                )))
                .orElseGet(List::of);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return decode(loginId)
                .map(principal -> normalizedRoleCodes(authorizationQueryPort.findRoleCodes(
                        principal.tenantId(),
                        principal.actorType(),
                        principal.actorId()
                )))
                .orElseGet(List::of);
    }

    private static Optional<AuthenticatedPrincipal> decode(Object loginId) {
        if (loginId == null) {
            return Optional.empty();
        }
        return SaTokenPrincipalCodec.decode(loginId);
    }

    private static List<String> normalizedRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null) {
            throw new IllegalStateException("角色编码查询结果不能为空");
        }
        return roleCodes.stream()
                .map(roleCode -> requireText(roleCode, "角色编码"))
                .sorted()
                .toList();
    }

    private static List<String> normalizedPermissionCodes(Collection<PermissionCode> permissionCodes) {
        if (permissionCodes == null) {
            throw new IllegalStateException("权限编码查询结果不能为空");
        }
        return permissionCodes.stream()
                .map(RbacStpInterface::requirePermissionCode)
                .sorted()
                .toList();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requirePermissionCode(PermissionCode permissionCode) {
        if (permissionCode == null) {
            throw new IllegalStateException("权限编码不能为空");
        }
        return permissionCode.value();
    }
}
