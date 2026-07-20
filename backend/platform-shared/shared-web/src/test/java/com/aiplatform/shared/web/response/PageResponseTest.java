package com.aiplatform.shared.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {
    @Test
    void slicesUsingOneBasedPageAndReportsMetadata() {
        PageResponse<Integer> response = PageResponse.from(List.of(1, 2, 3, 4, 5), 2, 2);

        assertThat(response.items()).containsExactly(3, 4);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
