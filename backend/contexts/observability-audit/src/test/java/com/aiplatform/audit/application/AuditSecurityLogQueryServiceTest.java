package com.aiplatform.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.audit.application.port.AuditSecurityLogQueryPort;
import com.aiplatform.audit.application.query.AuditSecurityLogQuery;
import com.aiplatform.audit.application.result.AuditSecurityLogPageResult;
import com.aiplatform.audit.application.result.AuditSecurityLogResult;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditSecurityLogQueryServiceTest {
    @Test
    void normalizesEveryFilterAndReturnsDatabaseBackedPageMetadata() {
        AuditSecurityLogQueryPort queryPort = mock(AuditSecurityLogQueryPort.class);
        AuditSecurityLogResult row = row();
        AuditSecurityLogQuery query = new AuditSecurityLogQuery(
                2001L,
                1002L,
                "IAM_ROLE_DEFAULT_MENU_UPDATE",
                "IAM_ROLE",
                "90323c3e-569a-4f13-ab3a-a5a115edc2b5",
                "trace-001",
                "HIGH",
                "FAILED",
                Instant.parse("2026-06-24T08:00:00Z"),
                Instant.parse("2026-06-25T08:00:00Z"),
                2,
                20
        );
        when(queryPort.countLogs(query)).thenReturn(41L);
        when(queryPort.findLogs(query)).thenReturn(List.of(row));
        AuditSecurityLogQueryService service = new AuditSecurityLogQueryService(queryPort);

        AuditSecurityLogPageResult result = service.findLogs(
                TenantId.of("2001"),
                " 1002 ",
                " iam_role_default_menu_update ",
                " iam_role ",
                " 90323c3e-569a-4f13-ab3a-a5a115edc2b5 ",
                " trace-001 ",
                " high ",
                " failed ",
                " 2026-06-24T08:00:00Z ",
                " 2026-06-25T08:00:00Z ",
                2,
                20
        );

        assertThat(result.items()).containsExactly(row);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(41);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(query.offset()).isEqualTo(20);
        assertThat(query.getOffset()).isEqualTo(20);
        verify(queryPort).countLogs(query);
        verify(queryPort).findLogs(query);
    }

    @Test
    void skipsThePageQueryWhenTheTenantHasNoMatchingRows() {
        AuditSecurityLogQueryPort queryPort = mock(AuditSecurityLogQueryPort.class);
        AuditSecurityLogQuery query = new AuditSecurityLogQuery(
                2001L, null, null, null, null, null, null, null, null, null, 1, 20
        );
        when(queryPort.countLogs(query)).thenReturn(0L);
        AuditSecurityLogQueryService service = new AuditSecurityLogQueryService(queryPort);

        AuditSecurityLogPageResult result = service.findLogs(
                TenantId.of("2001"), null, null, null, null, null, null, null, null, null, 1, 20
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(queryPort, never()).findLogs(query);
    }

    @Test
    void rejectsInvalidPaginationAndIsoTimeRangesBeforeCallingPersistence() {
        AuditSecurityLogQueryPort queryPort = mock(AuditSecurityLogQueryPort.class);
        AuditSecurityLogQueryService service = new AuditSecurityLogQueryService(queryPort);

        assertThatThrownBy(() -> service.findLogs(
                TenantId.of("2001"), null, null, null, null, null, null, null, null, null, 0, 20
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("页码");
        assertThatThrownBy(() -> service.findLogs(
                TenantId.of("2001"), null, null, null, null, null, null, null, null, null, 1, 0
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("每页条数");
        assertThatThrownBy(() -> service.findLogs(
                TenantId.of("2001"), null, null, null, null, null, null, null, null, null, 1, 101
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("每页条数");
        assertThatThrownBy(() -> service.findLogs(
                TenantId.of("2001"), null, null, null, null, null, null, null,
                "not-an-instant", null, 1, 20
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO-8601");
        assertThatThrownBy(() -> service.findLogs(
                TenantId.of("2001"), null, null, null, null, null, null, null,
                "2026-06-25T08:00:00Z", "2026-06-24T08:00:00Z", 1, 20
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("开始时间不能晚于结束时间");

        verifyNoInteractions(queryPort);
    }

    private static AuditSecurityLogResult row() {
        return new AuditSecurityLogResult(
                "118",
                "2001",
                "1002",
                "IAM_ROLE_DEFAULT_MENU_UPDATE",
                "HIGH",
                "IAM_ROLE",
                "3002",
                "90323c3e-569a-4f13-ab3a-a5a115edc2b5",
                "FAILED",
                "{}",
                "trace-001",
                Instant.parse("2026-06-24T08:00:00Z")
        );
    }
}
