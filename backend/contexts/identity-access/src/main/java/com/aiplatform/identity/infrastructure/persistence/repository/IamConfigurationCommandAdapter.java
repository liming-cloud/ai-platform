package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.application.command.AssignIamTenantAdministratorCommand;
import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.ReplaceIamMenuFeaturesCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.UpdateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamRoleDefaultMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.application.command.CreateIamTenantCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantCommand;
import com.aiplatform.identity.application.command.ChangeIamTenantStatusCommand;
import com.aiplatform.identity.application.error.IdentityErrorCodes;
import com.aiplatform.identity.application.port.IamConfigurationCommandPort;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * IAM 配置写入适配器，集中执行租户约束、候选集合校验和 MyBatis 写入。
 */
@Repository
public class IamConfigurationCommandAdapter implements IamConfigurationCommandPort {
    private final IamAuthorizationMapper mapper;

    public IamConfigurationCommandAdapter(IamAuthorizationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "IAM 授权 Mapper 不能为空");
    }

    @Override
    public String createTenant(CreateIamTenantCommand command) {
        if (mapper.countIamTenantByCode(command.code()) != 0) {
            throw new IllegalStateException("租户编码已存在");
        }
        Long tenantId = mapper.insertIamTenant(command.code(), command.name(), command.planCode());
        if (tenantId == null) throw new IllegalStateException("租户创建失败，未返回租户 ID");
        return tenantId.toString();
    }

    @Override
    public void updateTenant(UpdateIamTenantCommand command) {
        requireRows(mapper.updateIamTenant(tenantId(command.tenantId()), command.name(), command.planCode()), "租户更新");
    }

    @Override
    public void changeTenantStatus(ChangeIamTenantStatusCommand command) {
        requireRows(mapper.updateIamTenantStatus(tenantId(command.tenantId()), command.status()), "租户状态更新");
    }

    @Override
    public void assignTenantAdministrator(AssignIamTenantAdministratorCommand command) {
        Long tenantId = tenantId(command.tenantId());
        Long userId = parseRequiredLong(command.userId(), "用户 ID");
        Long roleId = parseRequiredLong(command.roleId(), "角色 ID");
        if (mapper.countActiveTenantUser(tenantId, userId) != 1) {
            throw resourceNotVisible(command.tenantId());
        }
        if (mapper.countActiveTenantAdminRole(tenantId, roleId) != 1) {
            throw resourceNotVisible(command.tenantId());
        }
        mapper.insertIamUserRole(
                tenantId,
                userId,
                roleId,
                "platform-delegation:" + actorId(command.actorId())
        );
    }

    @Override
    public String createMenu(CreateIamMenuCommand command) {
        requireExistingPermission(command.requiredPermissionCode());
        if (mapper.countIamMenuByCodeOrPath(command.code(), command.path()) != 0) {
            throw new IllegalStateException("菜单编码或路径已存在");
        }
        Long tenantId = tenantId(command.tenantId());
        if (!command.featureCodes().isEmpty()
                && mapper.countTenantFeatures(tenantId, command.featureCodes()) != command.featureCodes().size()) {
            throw new IllegalStateException("菜单绑定功能不属于当前租户功能目录");
        }
        Long menuId = mapper.insertIamMenu(
                command.code(),
                command.name(),
                command.scope(),
                command.path(),
                command.routeName(),
                command.componentKey(),
                command.icon(),
                command.requiredPermissionCode(),
                command.sort(),
                command.status(),
                actorId(command.actorId())
        );
        if (menuId == null) {
            throw new IllegalStateException("菜单创建失败，未返回菜单 ID");
        }
        replaceMenuFeatures(new ReplaceIamMenuFeaturesCommand(
                command.tenantId(),
                command.actorId(),
                command.traceId(),
                menuId.toString(),
                command.featureCodes()
        ));
        return menuId.toString();
    }

    @Override
    public void updateMenu(UpdateIamMenuCommand command) {
        Long menuId = parseRequiredLong(command.menuId(), "菜单 ID");
        requireExistingPermission(command.requiredPermissionCode());
        requireRows(mapper.updateIamMenu(
                menuId,
                command.name(),
                command.path(),
                command.routeName(),
                command.componentKey(),
                command.icon(),
                command.requiredPermissionCode(),
                command.sort(),
                command.status(),
                actorId(command.actorId())
        ), "菜单更新");
    }

    @Override
    public void replaceMenuFeatures(ReplaceIamMenuFeaturesCommand command) {
        Long menuId = parseRequiredLong(command.menuId(), "菜单 ID");
        if (mapper.countIamMenuById(menuId) != 1) {
            throw new IllegalStateException("菜单不存在或已删除");
        }
        if (!command.featureCodes().isEmpty()
                && mapper.countTenantFeatures(tenantId(command.tenantId()), command.featureCodes()) != command.featureCodes().size()) {
            throw new IllegalStateException("菜单绑定功能不属于当前租户功能目录");
        }
        mapper.deleteIamMenuFeatures(menuId);
        for (String featureCode : command.featureCodes()) {
            requireRows(mapper.insertIamMenuFeature(menuId, featureCode), "菜单功能绑定写入");
        }
    }

    @Override
    public void updateTenantFeature(UpdateIamTenantFeatureCommand command) {
        requireRows(mapper.updateIamTenantFeature(
                tenantId(command.tenantId()),
                command.featureCode(),
                command.enabled(),
                actorId(command.actorId())
        ), "租户功能更新");
    }

    @Override
    public void replaceRoleMenus(ReplaceIamRoleMenusCommand command) {
        Long tenantId = tenantId(command.tenantId());
        Long roleId = parseRequiredLong(command.roleId(), "角色 ID");
        requireManageableTenantRole(tenantId, roleId);
        List<Long> menuIds = parseIds(command.menuIds(), "菜单 ID");
        if (!menuIds.isEmpty() && mapper.countAssignableRoleMenus(tenantId, menuIds) != menuIds.size()) {
            throw new IllegalStateException("角色菜单包含不可分配菜单或未启用 feature 菜单");
        }
        mapper.softDeleteIamRoleMenus(tenantId, roleId, actorId(command.actorId()));
        for (Long menuId : menuIds) {
            requireRows(mapper.insertIamRoleMenu(tenantId, roleId, menuId, actorId(command.actorId())), "角色菜单写入");
        }
    }

    @Override
    public void replaceRolePermissions(ReplaceIamRolePermissionsCommand command) {
        Long tenantId = tenantId(command.tenantId());
        Long roleId = parseRequiredLong(command.roleId(), "角色 ID");
        requireManageableTenantRole(tenantId, roleId);
        if (!command.permissionCodes().isEmpty()
                && mapper.countAssignableRolePermissions(command.permissionCodes()) != command.permissionCodes().size()) {
            throw new IllegalArgumentException("角色权限包含不存在、停用或平台级权限");
        }
        mapper.softDeleteIamRolePermissions(tenantId, roleId, actorId(command.actorId()));
        for (String permissionCode : command.permissionCodes()) {
            requireRows(mapper.insertIamRolePermissionByCode(
                    tenantId,
                    roleId,
                    permissionCode,
                    actorId(command.actorId())
            ), "角色权限写入");
        }
    }

    @Override
    public void updateRoleDefaultMenu(UpdateIamRoleDefaultMenuCommand command) {
        Long tenantId = tenantId(command.tenantId());
        Long roleId = parseRequiredLong(command.roleId(), "角色 ID");
        requireManageableTenantRole(tenantId, roleId);
        if (mapper.countRoleAssignedMenuCode(tenantId, roleId, command.defaultMenuCode()) != 1) {
            throw new IllegalStateException("默认首页不在角色有效菜单集合内");
        }
        requireRows(mapper.updateIamRoleDefaultMenu(
                tenantId,
                roleId,
                command.defaultMenuCode(),
                actorId(command.actorId())
        ), "角色默认首页更新");
    }

    private void requireExistingPermission(String permissionCode) {
        if (mapper.countActivePermissionByCode(permissionCode) != 1) {
            throw new IllegalStateException("菜单权限编码不存在或未启用");
        }
    }

    private void requireManageableTenantRole(Long tenantId, Long roleId) {
        if (mapper.countManageableIamRoleByTenant(tenantId, roleId) != 1) {
            throw new ApplicationException(
                    IdentityErrorCodes.RESOURCE_NOT_VISIBLE,
                    "租户 ID=" + tenantId + "，角色不可维护"
            );
        }
    }

    private static ApplicationException resourceNotVisible(TenantId tenantId) {
        return new ApplicationException(
                IdentityErrorCodes.RESOURCE_NOT_VISIBLE,
                "被代管租户 ID=" + tenantId.value()
        );
    }

    private static Long tenantId(TenantId tenantId) {
        return parseRequiredLong(tenantId.value(), "租户 ID");
    }

    private static Long actorId(String actorId) {
        return parseRequiredLong(actorId, "操作者 ID");
    }

    private static List<Long> parseIds(List<String> ids, String fieldName) {
        return ids.stream()
                .map(id -> parseRequiredLong(id, fieldName))
                .distinct()
                .sorted()
                .toList();
    }

    private static void requireRows(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException(action + "失败，影响行数：" + affectedRows);
        }
    }
}
