package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingStatus;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity.PlatformRoleBindingEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL 平台角色写入存储，主体行锁串行化认证修订与角色状态变化。 */
public final class PostgresPlatformRoleBindingStore implements PlatformRoleBindingStore {

    private final PlatformRoleBindingMapper mapper;

    public PostgresPlatformRoleBindingStore(PlatformRoleBindingMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "平台角色绑定映射器不能为空");
    }

    @Override
    public Optional<SubjectIdentity> lock(SubjectId subjectId) {
        var entity = mapper.lockSubject(Objects.requireNonNull(subjectId, "主体标识不能为空").value());
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(SubjectIdentity.reconstitute(
                new SubjectId(entity.getSubjectId()),
                entity.getLoginName(),
                new EncodedPassword(entity.getPasswordHash()),
                SubjectIdentityStatus.valueOf(entity.getStatus()),
                entity.getVersion(),
                entity.getCreatedAt()));
    }

    @Override
    public boolean hasActiveBinding(SubjectId subjectId, PlatformRole role) {
        return mapper.hasActiveBinding(
                Objects.requireNonNull(subjectId, "主体标识不能为空").value(),
                Objects.requireNonNull(role, "平台角色不能为空").name());
    }

    @Override
    public Optional<PlatformRoleBinding> lockActiveBinding(SubjectId subjectId, PlatformRole role) {
        var entity = mapper.lockActiveBinding(
                Objects.requireNonNull(subjectId, "主体标识不能为空").value(),
                Objects.requireNonNull(role, "平台角色不能为空").name());
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public long countOtherActiveAccountAdministrators(SubjectId subjectId) {
        return mapper.countOtherActiveAccountAdministrators(
                Objects.requireNonNull(subjectId, "主体标识不能为空").value());
    }

    @Override
    public void bind(PlatformRoleBinding binding) {
        binding = Objects.requireNonNull(binding, "平台角色绑定不能为空");
        requireSingleWrite(mapper.insertBinding(
                binding.id().value(),
                binding.subjectId().value(),
                binding.role().name(),
                binding.createdAt()), "平台角色绑定写入失败");
        reviseAuthorization(binding.subjectId());
    }

    @Override
    public void revoke(PlatformRoleBinding binding) {
        binding = Objects.requireNonNull(binding, "平台角色绑定不能为空");
        if (binding.status() != PlatformRoleBindingStatus.REVOKED) {
            throw new IllegalArgumentException("只允许写入已撤销的平台角色绑定");
        }
        requireSingleWrite(mapper.revokeBinding(
                binding.id().value(),
                binding.version(),
                binding.version() - 1L,
                binding.revokedAt()), "平台角色绑定撤销冲突");
        reviseAuthorization(binding.subjectId());
    }

    private static PlatformRoleBinding toDomain(PlatformRoleBindingEntity entity) {
        return PlatformRoleBinding.reconstitute(
                new com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId(
                        entity.getBindingId()),
                new SubjectId(entity.getSubjectId()),
                PlatformRole.from(entity.getRoleCode()),
                PlatformRoleBindingStatus.valueOf(entity.getStatus()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getRevokedAt());
    }

    /**
     * 主体锁已由调用方持有，先推进既有修订；首次绑定才建立认证事实。
     *
     * <p>不用 PostgreSQL UPSERT，避免安全更新拦截器将该语句误判为无条件写入。</p>
     */
    private void reviseAuthorization(SubjectId subjectId) {
        if (mapper.advanceAuthorizationRevision(subjectId.value()) == 0) {
            requireSingleWrite(
                    mapper.establishAuthorizationRevision(subjectId.value()), "平台权限修订建立失败");
        }
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
