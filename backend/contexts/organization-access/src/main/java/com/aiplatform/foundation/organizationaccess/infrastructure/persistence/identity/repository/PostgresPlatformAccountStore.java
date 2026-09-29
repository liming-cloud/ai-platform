package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Objects;
import java.util.Optional;
import java.util.Arrays;
import java.util.Set;

/** PostgreSQL 平台账户存储，使用行锁读取并以修订号原子更新。 */
public final class PostgresPlatformAccountStore implements PlatformAccountStore {

    private final PlatformAccountMapper mapper;

    public PostgresPlatformAccountStore(PlatformAccountMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "平台账户映射器不能为空");
    }

    @Override
    public void lockAdministratorDisableGate() {
        mapper.lockAdministratorDisableGate();
    }

    @Override
    public Optional<SubjectIdentity> lock(SubjectId subjectId) {
        var row = mapper.lock(subjectId.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(SubjectIdentity.reconstitute(
                new SubjectId(row.getSubjectId()),
                row.getLoginName(),
                new EncodedPassword(row.getPasswordHash()),
                SubjectIdentityStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt()));
    }

    @Override
    public boolean hasActiveAccountAdministratorRole(SubjectId subjectId) {
        return mapper.hasActiveAccountAdministratorRole(subjectId.value());
    }

    @Override
    public long countOtherActiveAccountAdministrators(SubjectId subjectId) {
        return mapper.countOtherActiveAccountAdministrators(subjectId.value());
    }

    @Override
    public boolean insert(SubjectIdentity identity) {
        return mapper.insert(
                identity.id().value(),
                identity.loginName(),
                identity.encodedPassword().verificationValue(),
                identity.status().name(),
                identity.version(),
                identity.createdAt()) == 1;
    }

    @Override
    public void update(SubjectIdentity identity) {
        var updated = mapper.update(
                identity.id().value(),
                identity.encodedPassword().verificationValue(),
                identity.status().name(),
                identity.version(),
                identity.version() - 1L);
        if (updated != 1) {
            throw new IllegalStateException("平台账户更新冲突");
        }
    }

    @Override
    public PageResult<PlatformAccountSummary> list(PageQuery query, String loginName, String status) {
        var page = mapper.list(new Page<>(query.pageNumber(), query.pageSize()), loginName, status);
        var items = page.getRecords().stream()
                .map(row -> new PlatformAccountSummary(
                        row.getSubjectId(), row.getLoginName(), row.getStatus(), row.getVersion(), row.getCreatedAt(),
                        activeRoleCodes(row.getActiveRoleCodes()),
                        row.getVersion() == 0 ? null : row.getUpdatedAt()))
                .toList();
        return PageResult.of(items, query, page.getTotal());
    }

    @Override
    public void recordOperation(long operatorSubjectId, long targetSubjectId, String operation, long targetVersion) {
        if (mapper.recordOperation(operatorSubjectId, targetSubjectId, operation, targetVersion) != 1) {
            throw new IllegalStateException("平台账户操作审计写入失败");
        }
    }

    private static Set<String> activeRoleCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList(codes.split(",")));
    }
}
