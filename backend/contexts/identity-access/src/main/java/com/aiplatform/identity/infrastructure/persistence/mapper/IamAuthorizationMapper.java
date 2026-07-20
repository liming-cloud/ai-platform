package com.aiplatform.identity.infrastructure.persistence.mapper;

import com.aiplatform.identity.infrastructure.persistence.entity.IamResourceAclPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamNotificationOutboxPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserInvitationOverviewPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamConsoleMenuPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamMenuConfigurationPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamPermissionPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamRolePersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamTenantFeaturePersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamTenantConfigurationPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserConfigurationPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserInvitationPersistenceEntity;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RBAC 授权查询 Mapper，所有 SQL 统一放在 XML 中，避免权限规则散落在 Java 注解。
 */
@Mapper
public interface IamAuthorizationMapper {
    List<IamTenantConfigurationPersistenceEntity> findIamTenants();

    int countIamTenantByCode(@Param("tenantCode") String tenantCode);

    Long insertIamTenant(@Param("tenantCode") String tenantCode, @Param("tenantName") String tenantName,
                         @Param("planCode") String planCode);

    int updateIamTenant(@Param("tenantId") Long tenantId, @Param("tenantName") String tenantName,
                        @Param("planCode") String planCode);

    int updateIamTenantStatus(@Param("tenantId") Long tenantId, @Param("status") String status);

    Set<String> findActiveRoleCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    Set<String> findActivePermissionCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    List<IamConsoleMenuPersistenceEntity> findAuthorizedConsoleMenus(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("permissionCodes") Set<String> permissionCodes
    );

    List<String> findActiveRoleDefaultMenuCodes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    List<IamMenuConfigurationPersistenceEntity> findIamConfigurationMenus(@Param("tenantId") Long tenantId);

    List<IamTenantFeaturePersistenceEntity> findIamTenantFeatures(@Param("tenantId") Long tenantId);

    List<IamRolePersistenceEntity> findIamRoles(@Param("tenantId") Long tenantId);

    long countIamUsers(@Param("tenantId") Long tenantId);

    List<IamUserConfigurationPersistenceEntity> findIamUsers(
            @Param("tenantId") Long tenantId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    List<IamPermissionPersistenceEntity> findIamPermissions(@Param("tenantId") Long tenantId);

    List<IamMenuConfigurationPersistenceEntity> findIamRoleMenus(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId
    );

    List<IamPermissionPersistenceEntity> findIamRolePermissions(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId
    );

    int countActivePermissionByCode(@Param("permissionCode") String permissionCode);

    int countIamMenuById(@Param("menuId") Long menuId);

    int countIamMenuByCodeOrPath(@Param("code") String code, @Param("path") String path);

    int countTenantFeatures(@Param("tenantId") Long tenantId, @Param("featureCodes") List<String> featureCodes);

    Long insertIamMenu(
            @Param("code") String code,
            @Param("name") String name,
            @Param("scope") String scope,
            @Param("path") String path,
            @Param("routeName") String routeName,
            @Param("componentKey") String componentKey,
            @Param("icon") String icon,
            @Param("requiredPermissionCode") String requiredPermissionCode,
            @Param("sort") int sort,
            @Param("status") String status,
            @Param("actorId") Long actorId
    );

    int countActiveTenantUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int countActiveTenantAdminRole(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    int insertIamUserRole(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("grantSource") String grantSource
    );

    int countInvitationAssignableRole(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    int countTenantUserByUsernameOrEmail(@Param("tenantId") Long tenantId, @Param("username") String username, @Param("email") String email);

    Long insertPendingActivationUser(@Param("tenantId") Long tenantId, @Param("username") String username,
            @Param("displayName") String displayName, @Param("email") String email,
            @Param("passwordHash") String passwordHash, @Param("actorId") Long actorId);

    int insertUserInvitation(@Param("invitationId") String invitationId, @Param("tenantId") Long tenantId,
            @Param("userId") Long userId, @Param("roleId") Long roleId, @Param("email") String email,
            @Param("tokenDigest") String tokenDigest, @Param("expiresAt") Instant expiresAt, @Param("actorId") Long actorId);

    int insertInvitationNotification(@Param("invitationId") String invitationId, @Param("tenantId") Long tenantId,
                                     @Param("recipient") String recipient, @Param("payloadJson") String payloadJson);

    List<IamNotificationOutboxPersistenceEntity> findQueuedInvitationNotifications(@Param("limit") int limit);

    int claimInvitationNotification(@Param("id") Long id);

    int markInvitationNotificationDelivered(@Param("id") Long id);

    int markInvitationNotificationFailed(@Param("id") Long id, @Param("lastError") String lastError);

    int expireInvitationNotificationPayloads();

    int clearInvitationNotificationPayloads(
            @Param("tenantId") Long tenantId,
            @Param("invitationId") String invitationId,
            @Param("terminalStatus") String terminalStatus
    );

    List<IamUserInvitationOverviewPersistenceEntity> findUserInvitations(@Param("tenantId") Long tenantId);

    IamUserInvitationOverviewPersistenceEntity findUserInvitation(@Param("tenantId") Long tenantId, @Param("invitationId") String invitationId);

    int reissueUserInvitation(@Param("tenantId") Long tenantId, @Param("invitationId") String invitationId,
                              @Param("tokenDigest") String tokenDigest, @Param("expiresAt") Instant expiresAt);

    int revokeUserInvitation(@Param("tenantId") Long tenantId, @Param("invitationId") String invitationId);

    int disablePendingInvitationUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("actorId") Long actorId);

    IamUserInvitationPersistenceEntity findPendingUserInvitationByDigest(@Param("tokenDigest") String tokenDigest);

    int activateInvitedUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    int acceptUserInvitation(@Param("invitationId") String invitationId, @Param("activatedAt") Instant activatedAt);

    int countManageableIamRoleByTenant(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    int countAssignableRoleMenus(@Param("tenantId") Long tenantId, @Param("menuIds") List<Long> menuIds);

    int countAssignableRolePermissions(@Param("permissionCodes") List<String> permissionCodes);

    int countRoleAssignedMenuCode(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId,
            @Param("menuCode") String menuCode
    );

    int updateIamMenu(
            @Param("menuId") Long menuId,
            @Param("name") String name,
            @Param("path") String path,
            @Param("routeName") String routeName,
            @Param("componentKey") String componentKey,
            @Param("icon") String icon,
            @Param("requiredPermissionCode") String requiredPermissionCode,
            @Param("sort") int sort,
            @Param("status") String status,
            @Param("actorId") Long actorId
    );

    int deleteIamMenuFeatures(@Param("menuId") Long menuId);

    int insertIamMenuFeature(@Param("menuId") Long menuId, @Param("featureCode") String featureCode);

    int updateIamTenantFeature(
            @Param("tenantId") Long tenantId,
            @Param("featureCode") String featureCode,
            @Param("enabled") boolean enabled,
            @Param("actorId") Long actorId
    );

    int softDeleteIamRoleMenus(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId,
            @Param("actorId") Long actorId
    );

    int insertIamRoleMenu(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId,
            @Param("menuId") Long menuId,
            @Param("actorId") Long actorId
    );

    int softDeleteIamRolePermissions(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId,
            @Param("actorId") Long actorId
    );

    int insertIamRolePermissionByCode(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId,
            @Param("permissionCode") String permissionCode,
            @Param("actorId") Long actorId
    );

    int updateIamRoleDefaultMenu(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId,
            @Param("defaultMenuCode") String defaultMenuCode,
            @Param("actorId") Long actorId
    );

    List<IamResourceAclPersistenceEntity> findActiveResourceAcls(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("permissionCode") String permissionCode
    );

    List<Long> findAllowedResourceUserIds(
            @Param("tenantId") Long tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("permissionCode") String permissionCode
    );

    int countActiveAclSubject(
            @Param("tenantId") Long tenantId,
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId
    );

    int softDeleteResourceAcls(
            @Param("tenantId") Long tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("permissionCode") String permissionCode,
            @Param("actorId") Long actorId
    );

    int insertResourceAcl(
            @Param("tenantId") Long tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("permissionCode") String permissionCode,
            @Param("actorId") Long actorId
    );

    int countActiveResourceAcl(
            @Param("tenantId") Long tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId,
            @Param("permissionCode") String permissionCode
    );
}
