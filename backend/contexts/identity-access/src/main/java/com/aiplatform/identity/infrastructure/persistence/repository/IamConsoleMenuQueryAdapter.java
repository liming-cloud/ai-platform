package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.application.port.ConsoleMenuQueryPort;
import com.aiplatform.identity.application.result.AuthorizedConsoleMenuSnapshot;
import com.aiplatform.identity.application.result.CurrentUserMenuResult;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 控制台菜单查询适配器，只从 DB 中恢复角色菜单、权限和租户功能事实，不保留代码硬编码菜单目录。
 */
@Repository
public class IamConsoleMenuQueryAdapter implements ConsoleMenuQueryPort {
    private static final String USER_ACTOR_TYPE = "user";

    private final IamAuthorizationMapper mapper;

    public IamConsoleMenuQueryAdapter(IamAuthorizationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "授权查询 Mapper 不能为空");
    }

    @Override
    public AuthorizedConsoleMenuSnapshot findAuthorizedMenus(
            TenantId tenantId,
            String actorType,
            String actorId,
            Set<String> roleCodes,
            Set<PermissionCode> permissionCodes
    ) {
        if (!USER_ACTOR_TYPE.equals(actorType)) {
            return new AuthorizedConsoleMenuSnapshot(List.of(), "/403");
        }
        Long tenantDatabaseId = parseTenantId(tenantId);
        Long userDatabaseId = parseRequiredLong(actorId, "actorId");
        Set<String> permissions = normalizePermissionCodes(permissionCodes);
        if (permissions.isEmpty()) {
            return new AuthorizedConsoleMenuSnapshot(List.of(), "/403");
        }
        List<CurrentUserMenuResult> menus = requiredCollection(
                mapper.findAuthorizedConsoleMenus(tenantDatabaseId, userDatabaseId, permissions),
                "授权菜单查询结果"
        ).stream().map(menu -> Objects.requireNonNull(menu, "授权菜单行不能为空").toResult()).toList();
        List<String> defaultMenuCodes = requiredCollection(
                mapper.findActiveRoleDefaultMenuCodes(tenantDatabaseId, userDatabaseId),
                "默认菜单编码查询结果"
        ).stream().map(code -> requireText(code, "默认菜单编码")).toList();
        return AuthorizedConsoleMenuSnapshot.from(menus, defaultMenuCodes);
    }

    private static Long parseTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return parseRequiredLong(tenantId.value(), "tenantId");
    }

    private static Set<String> normalizePermissionCodes(Set<PermissionCode> permissionCodes) {
        Objects.requireNonNull(permissionCodes, "权限编码集合不能为空");
        return permissionCodes.stream()
                .map(permissionCode -> Objects.requireNonNull(permissionCode, "权限编码不能为空").value())
                .map(code -> requireText(code, "权限编码"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static <T> Collection<T> requiredCollection(Collection<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return values;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
