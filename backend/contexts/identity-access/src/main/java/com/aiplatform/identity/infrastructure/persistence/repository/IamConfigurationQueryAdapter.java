package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.application.port.IamConfigurationQueryPort;
import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import com.aiplatform.identity.application.result.IamPermissionResult;
import com.aiplatform.identity.application.result.IamRoleResult;
import com.aiplatform.identity.application.result.IamTenantFeatureResult;
import com.aiplatform.identity.application.result.IamTenantResult;
import com.aiplatform.identity.application.result.IamUserConfigurationResult;
import com.aiplatform.identity.application.result.IamUserConfigurationPage;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * IAM 配置读取适配器，只读 DB 事实源，不承担配置保存职责。
 */
@Repository
public class IamConfigurationQueryAdapter implements IamConfigurationQueryPort {
    private final IamAuthorizationMapper mapper;

    public IamConfigurationQueryAdapter(IamAuthorizationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "授权查询 Mapper 不能为空");
    }

    @Override
    public List<IamTenantResult> findTenants() {
        return requiredCollection(mapper.findIamTenants(), "IAM 租户查询结果").stream()
                .map(row -> Objects.requireNonNull(row, "IAM 租户行不能为空").toResult())
                .toList();
    }

    @Override
    public List<IamMenuConfigurationResult> findMenus(TenantId tenantId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        return requiredCollection(mapper.findIamConfigurationMenus(tenantDatabaseId), "IAM 菜单配置查询结果")
                .stream()
                .map(row -> Objects.requireNonNull(row, "IAM 菜单配置行不能为空").toResult())
                .toList();
    }

    @Override
    public List<IamTenantFeatureResult> findTenantFeatures(TenantId tenantId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        return requiredCollection(mapper.findIamTenantFeatures(tenantDatabaseId), "IAM 租户功能查询结果")
                .stream()
                .map(row -> Objects.requireNonNull(row, "IAM 租户功能行不能为空").toResult())
                .toList();
    }

    @Override
    public List<IamRoleResult> findRoles(TenantId tenantId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        return requiredCollection(mapper.findIamRoles(tenantDatabaseId), "IAM 角色查询结果")
                .stream()
                .map(row -> Objects.requireNonNull(row, "IAM 角色行不能为空").toResult())
                .toList();
    }

    @Override
    public IamUserConfigurationPage findUsers(TenantId tenantId, int page, int pageSize) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        long total = mapper.countIamUsers(tenantDatabaseId);
        if (total < 0) {
            throw new IllegalStateException("IAM 用户总数不能小于 0");
        }
        int totalPages = total == 0 ? 0 : Math.toIntExact(Math.ceilDiv(total, (long) pageSize));
        int effectivePage = totalPages == 0 ? 1 : Math.min(page, totalPages);
        long offset = Math.multiplyExact((long) effectivePage - 1, pageSize);
        List<IamUserConfigurationResult> items = total == 0
                ? List.of()
                : requiredCollection(
                        mapper.findIamUsers(tenantDatabaseId, pageSize, offset),
                        "IAM 用户查询结果"
                ).stream()
                .map(row -> Objects.requireNonNull(row, "IAM 用户行不能为空").toResult())
                .toList();
        return new IamUserConfigurationPage(items, total, effectivePage, pageSize);
    }

    @Override
    public List<IamPermissionResult> findPermissions(TenantId tenantId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        return requiredCollection(mapper.findIamPermissions(tenantDatabaseId), "IAM 权限目录查询结果")
                .stream()
                .map(row -> Objects.requireNonNull(row, "IAM 权限目录行不能为空").toResult())
                .toList();
    }

    @Override
    public List<IamMenuConfigurationResult> findRoleMenus(TenantId tenantId, String roleId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        Long roleDatabaseId = parseRequiredLong(roleId, "角色 ID");
        return requiredCollection(mapper.findIamRoleMenus(tenantDatabaseId, roleDatabaseId), "IAM 角色菜单查询结果")
                .stream()
                .map(row -> Objects.requireNonNull(row, "IAM 角色菜单行不能为空").toResult())
                .toList();
    }

    @Override
    public List<IamPermissionResult> findRolePermissions(TenantId tenantId, String roleId) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        Long roleDatabaseId = parseRequiredLong(roleId, "角色 ID");
        return requiredCollection(mapper.findIamRolePermissions(tenantDatabaseId, roleDatabaseId), "IAM 角色权限查询结果")
                .stream()
                .map(row -> Objects.requireNonNull(row, "IAM 角色权限行不能为空").toResult())
                .toList();
    }

    private static Long parseTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return parseRequiredLong(tenantId.value(), "租户 ID");
    }

    private static <T> Collection<T> requiredCollection(Collection<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return values;
    }
}
