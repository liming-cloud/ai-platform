package org.fujay.ai.platform.tenant.domain.repository;

import java.util.List;
import java.util.Optional;

public interface TenantAccessRepository {

    Optional<TenantRecord> findTenantByCode(String tenantCode);

    Optional<TenantRecord> findTenantById(Long tenantId);

    Optional<UserRecord> findUserByUsername(Long tenantId, String username);

    Optional<UserRecord> findUserById(Long tenantId, Long userId);

    Optional<CredentialRecord> findCredential(Long tenantId, Long userId);

    List<RoleRecord> findRolesByUserId(Long tenantId, Long userId);

    List<PermissionRecord> findPermissionsByUserId(Long tenantId, Long userId);

    List<MenuRecord> findMenusByUserId(Long tenantId, Long userId);

    List<TenantRecord> listTenants();

    Optional<TenantRecord> updateTenant(Long tenantId, String tenantName, String status);

    Optional<TenantRecord> updateTenantStatus(Long tenantId, String status);

    List<RoleRecord> listRoles(Long tenantId);

    List<PermissionRecord> listPermissions(Long tenantId);

    List<MenuRecord> listMenus(Long tenantId);

    List<PermissionRecord> findPermissionsByRoleId(Long tenantId, Long roleId);

    List<MenuRecord> findMenusByRoleId(Long tenantId, Long roleId);

    Optional<RoleRecord> saveRole(Long tenantId, Long roleId, String roleCode, String roleName, String status);

    Optional<PermissionRecord> savePermission(
            Long tenantId, Long permissionId, String permissionCode, String permissionName, String status);

    Optional<MenuRecord> saveMenu(
            Long tenantId,
            Long menuId,
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder,
            String status);

    Optional<RoleAccessRecord> updateRoleBindings(
            Long tenantId, Long roleId, List<String> permissionCodes, List<String> menuCodes);

    void bootstrapTenantUser(BootstrapTenant tenant, BootstrapUser user, String passwordHash);

    void appendAudit(Long tenantId, Long actorId, String action, String result, String detail);

    record TenantRecord(Long id, String tenantCode, String tenantName, String status) {
    }

    record UserRecord(Long id, Long tenantId, String username, String displayName, String status) {
    }

    record CredentialRecord(Long id, Long tenantId, Long userId, String passwordHash) {
    }

    record RoleRecord(Long id, Long tenantId, String roleCode, String roleName, String status) {
    }

    record PermissionRecord(Long id, Long tenantId, String permissionCode, String permissionName, String status) {
    }

    record MenuRecord(
            Long id,
            Long tenantId,
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder,
            String status) {
    }

    record RoleAccessRecord(RoleRecord role, List<PermissionRecord> permissions, List<MenuRecord> menus) {
    }

    record BootstrapTenant(String tenantCode, String tenantName) {
    }

    record BootstrapUser(String username, String displayName) {
    }
}
