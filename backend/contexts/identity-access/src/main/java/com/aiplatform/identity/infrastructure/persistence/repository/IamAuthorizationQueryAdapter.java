package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.domain.permission.ResourceAcl;
import com.aiplatform.identity.domain.permission.ResourceScope;
import com.aiplatform.identity.domain.permission.spec.AuthorizationDecisionSpec;
import com.aiplatform.identity.infrastructure.persistence.converter.IamResourceAclPersistenceConverter;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.shared.security.port.ResourceAudienceQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * identity-access 对平台 RBAC 查询端口的实现，所有授权判断均从服务端权限目录和 ACL 快照恢复。
 */
@Repository
public class IamAuthorizationQueryAdapter implements RbacAuthorizationQueryPort, PermissionCheckPort, ResourceAudienceQueryPort {
    private static final String USER_ACTOR_TYPE = "user";

    private final IamAuthorizationMapper mapper;
    private final IamResourceAclPersistenceConverter aclConverter;
    private final AuthorizationDecisionSpec decisionSpec;
    private final Clock clock;

    public IamAuthorizationQueryAdapter(
            IamAuthorizationMapper mapper,
            IamResourceAclPersistenceConverter aclConverter,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "授权查询 Mapper 不能为空");
        this.aclConverter = Objects.requireNonNull(aclConverter, "资源 ACL 转换器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        this.decisionSpec = new AuthorizationDecisionSpec();
    }

    @Override
    public Set<String> findRoleCodes(TenantId tenantId, String actorType, String actorId) {
        if (!USER_ACTOR_TYPE.equals(actorType)) {
            return Set.of();
        }
        return requiredCollection(
                mapper.findActiveRoleCodes(parseTenantId(tenantId), parseRequiredLong(actorId, "actorId")),
                "角色编码查询结果"
        )
                .stream()
                .map(roleCode -> requireText(roleCode, "角色编码"))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<PermissionCode> findPermissionCodes(TenantId tenantId, String actorType, String actorId) {
        if (!USER_ACTOR_TYPE.equals(actorType)) {
            return Set.of();
        }
        return requiredCollection(
                mapper.findActivePermissionCodes(parseTenantId(tenantId), parseRequiredLong(actorId, "actorId")),
                "权限编码查询结果"
        )
                .stream()
                .map(PermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean canAccess(
            TenantId tenantId,
            String actorId,
            PermissionCode permissionCode,
            String resourceType,
            String resourceId
    ) {
        Objects.requireNonNull(permissionCode, "权限编码不能为空");
        Long tenantDatabaseId = parseTenantId(tenantId);
        Long userDatabaseId = parseRequiredLong(actorId, "actorId");
        Long resourceDatabaseId = parseRequiredLong(resourceId, "resourceId");
        Set<PermissionCode> permissions = findPermissionCodes(tenantId, USER_ACTOR_TYPE, actorId);
        List<ResourceAcl> acls = requiredCollection(
                mapper.findActiveResourceAcls(
                        tenantDatabaseId,
                        userDatabaseId,
                        resourceType,
                        resourceDatabaseId,
                        permissionCode.value()
                ),
                "资源 ACL 查询结果"
        )
                .stream()
                .map(aclConverter::toDomain)
                .toList();
        return decisionSpec.decide(
                permissions,
                acls,
                new ResourceScope(resourceType, resourceId),
                permissionCode,
                Instant.now(clock)
        ).allowed();
    }

    @Override
    public Set<String> findAllowedUserIds(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            PermissionCode permissionCode
    ) {
        Objects.requireNonNull(permissionCode, "权限编码不能为空");
        return requiredCollection(
                mapper.findAllowedResourceUserIds(
                        parseTenantId(tenantId),
                        requireText(resourceType, "资源类型"),
                        parseRequiredLong(resourceId, "resourceId"),
                        permissionCode.value()
                ),
                "资源授权用户查询结果"
        ).stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static Long parseTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return parseRequiredLong(tenantId.value(), "tenantId");
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
