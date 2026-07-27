package com.aiplatform.foundation.shared.contract.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    @Test
    void 错误详情对调用方不可修改() {
        var response = new ApiErrorResponse(
                "COMMON.REQUEST.0001",
                "请求格式不正确",
                "trc_1001",
                Map.of("field", "pageNumber"));

        assertEquals("COMMON.REQUEST.0001", response.code());
        assertThrows(
                UnsupportedOperationException.class,
                () -> response.details().put("sql", "不得公开"));
    }
}
