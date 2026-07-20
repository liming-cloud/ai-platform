package com.aiplatform.approval.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ListApprovalTasksCommandTest {
    @Test
    void keepsExplicitMissingStatusAsEmptyOptional() {
        ListApprovalTasksCommand command = new ListApprovalTasksCommand(
                TenantId.of("2001"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                1,
                20
        );

        assertThat(command.status()).isEmpty();
        assertThat(command.page()).isEqualTo(1);
        assertThat(command.pageSize()).isEqualTo(20);
    }

    @Test
    void rejectsNullStatusOptionalInsteadOfListingAll() {
        assertThatThrownBy(() -> new ListApprovalTasksCommand(
                TenantId.of("2001"), null, Optional.empty(), Optional.empty(), "", 1, 20
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("审批任务状态查询条件不能为空");
    }

    @Test
    void rejectsInvalidDatabasePageBoundsInsteadOfSilentlyClamping() {
        assertThatThrownBy(() -> new ListApprovalTasksCommand(
                TenantId.of("2001"), Optional.empty(), Optional.empty(), Optional.empty(), "", 0, 20
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("页码必须大于 0");
        assertThatThrownBy(() -> new ListApprovalTasksCommand(
                TenantId.of("2001"), Optional.empty(), Optional.empty(), Optional.empty(), "", 1, 101
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("每页条数必须为 1 到 100");
    }
}
