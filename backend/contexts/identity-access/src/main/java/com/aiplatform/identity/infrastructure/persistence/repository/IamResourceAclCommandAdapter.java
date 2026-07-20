package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IamResourceAclCommandAdapter implements ResourceAclCommandPort {
    private final IamAuthorizationMapper mapper;

    public IamResourceAclCommandAdapter(IamAuthorizationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "IAM 授权 Mapper 不能为空");
    }

    @Override
    @Transactional
    public void grantAllowingSubject(TenantId tenantId, String resourceType, String resourceId,
                                     PermissionCode permissionCode, ResourceAclSubject subject, String actorId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(permissionCode, "权限编码不能为空");
        Objects.requireNonNull(subject, "ACL 主体不能为空");
        Long tenantDatabaseId = parseRequiredLong(tenantId.value(), "租户 ID");
        Long resourceDatabaseId = parseRequiredLong(resourceId, "资源 ID");
        Long subjectId = parseRequiredLong(subject.subjectId(), "ACL 主体 ID");
        Long actorDatabaseId = parseRequiredLong(actorId, "操作者 ID");
        String normalizedResourceType = requireText(resourceType, "资源类型");
        if (mapper.countActiveAclSubject(tenantDatabaseId, subject.subjectType(), subjectId) != 1) {
            throw new IllegalArgumentException("ACL 主体不存在或不可用");
        }
        if (mapper.countActiveResourceAcl(tenantDatabaseId, normalizedResourceType, resourceDatabaseId,
                subject.subjectType(), subjectId, permissionCode.value()) == 0) {
            int inserted = mapper.insertResourceAcl(tenantDatabaseId, normalizedResourceType, resourceDatabaseId,
                    subject.subjectType(), subjectId, permissionCode.value(), actorDatabaseId);
            if (inserted != 1) throw new IllegalStateException("资源 ACL 授权失败");
        }
    }

    @Override
    @Transactional
    public void replaceAllowingSubjects(
            TenantId tenantId,
            String resourceType,
            String resourceId,
            PermissionCode permissionCode,
            Set<ResourceAclSubject> subjects,
            String actorId
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(permissionCode, "权限编码不能为空");
        Objects.requireNonNull(subjects, "ACL 主体集合不能为空");
        Long tenantDatabaseId = parseRequiredLong(tenantId.value(), "租户 ID");
        Long resourceDatabaseId = parseRequiredLong(resourceId, "资源 ID");
        Long actorDatabaseId = parseRequiredLong(actorId, "操作者 ID");
        String normalizedResourceType = requireText(resourceType, "资源类型");

        for (ResourceAclSubject subject : subjects) {
            Long subjectId = parseRequiredLong(subject.subjectId(), "ACL 主体 ID");
            if (mapper.countActiveAclSubject(tenantDatabaseId, subject.subjectType(), subjectId) != 1) {
                throw new IllegalArgumentException("ACL 主体不存在或不可用");
            }
        }

        mapper.softDeleteResourceAcls(
                tenantDatabaseId,
                normalizedResourceType,
                resourceDatabaseId,
                permissionCode.value(),
                actorDatabaseId
        );
        for (ResourceAclSubject subject : subjects) {
            int inserted = mapper.insertResourceAcl(
                    tenantDatabaseId,
                    normalizedResourceType,
                    resourceDatabaseId,
                    subject.subjectType(),
                    parseRequiredLong(subject.subjectId(), "ACL 主体 ID"),
                    permissionCode.value(),
                    actorDatabaseId
            );
            if (inserted != 1) {
                throw new IllegalStateException("资源 ACL 保存失败");
            }
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
