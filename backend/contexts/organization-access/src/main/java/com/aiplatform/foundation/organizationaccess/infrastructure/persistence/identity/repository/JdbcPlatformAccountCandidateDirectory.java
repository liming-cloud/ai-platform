package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountCandidateDirectory;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 只选择账户引用、登录名和状态的 PostgreSQL 候选目录。 */
public final class JdbcPlatformAccountCandidateDirectory implements PlatformAccountCandidateDirectory {

    private final JdbcClient jdbc;

    public JdbcPlatformAccountCandidateDirectory(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 客户端不能为空");
    }

    @Override
    public PageResult<PlatformAccountCandidate> search(String query, PageQuery page) {
        var pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        var total = jdbc.sql("""
                        SELECT COUNT(*) FROM organization_access.subject_identity
                        WHERE lower(login_name) LIKE :pattern ESCAPE '\\'
                        """)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        var items = jdbc.sql("""
                        SELECT subject_id, login_name, status
                        FROM organization_access.subject_identity
                        WHERE lower(login_name) LIKE :pattern ESCAPE '\\'
                        ORDER BY lower(login_name), subject_id
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
