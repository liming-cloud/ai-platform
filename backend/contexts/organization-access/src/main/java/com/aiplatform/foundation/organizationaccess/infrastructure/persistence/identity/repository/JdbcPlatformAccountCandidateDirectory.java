package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.candidate.PlatformAccountCandidateDirectory;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只选择符合初始租户管理员资格的账户引用、登录名和状态。 */
public final class JdbcPlatformAccountCandidateDirectory implements PlatformAccountCandidateDirectory {

    private final JdbcClient jdbc;

    public JdbcPlatformAccountCandidateDirectory(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public PageResult<PlatformAccountCandidate> search(String query, PageQuery page) {
        var pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        var total = jdbc.sql("""
                        SELECT COUNT(*)
                        FROM organization_access.subject_identity subject
                        WHERE subject.status = 'ACTIVE'
                          AND lower(subject.login_name) LIKE :pattern ESCAPE '\\'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM organization_access.platform_role_binding binding
                              WHERE binding.subject_id = subject.subject_id
                                AND binding.role_code = 'PLATFORM_ADMINISTRATOR'
                                AND binding.status = 'ACTIVE'
                          )
                        """)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        SELECT subject.subject_id, subject.login_name, subject.status
                        FROM organization_access.subject_identity subject
                        WHERE subject.status = 'ACTIVE'
                          AND lower(subject.login_name) LIKE :pattern ESCAPE '\\'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM organization_access.platform_role_binding binding
                              WHERE binding.subject_id = subject.subject_id
                                AND binding.role_code = 'PLATFORM_ADMINISTRATOR'
                                AND binding.status = 'ACTIVE'
                          )
                        ORDER BY lower(subject.login_name), subject.subject_id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("pattern", pattern)
                .param("limit", page.pageSize())
                .param("offset", page.offset())
                .query((row, rowNumber) -> new PlatformAccountCandidate(
                        Long.toString(row.getLong("subject_id")),
                        row.getString("login_name"),
                        row.getString("status")))
                .list();
        return PageResult.of(items, page, total);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
