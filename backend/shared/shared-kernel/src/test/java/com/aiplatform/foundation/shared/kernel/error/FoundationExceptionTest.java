package com.aiplatform.foundation.shared.kernel.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FoundationExceptionTest {

    @Test
    void 只保留错误码允许公开的详情() {
        var exception = new ApplicationException(
                TestError.QUOTA,
                Map.of(
                        "limit", 20,
                        "credential", "不得公开"));

        assertEquals("TEST.QUOTA.0001", exception.errorCode().code());
        assertEquals("请求配额已用尽", exception.getMessage());
        assertEquals(Map.of("limit", 20), exception.safeDetails());
        assertFalse(exception.retryable());
    }

    @Test
    void 错误编码格式不正确时拒绝创建异常() {
        var invalid = new ErrorCode() {
            @Override
            public String code() {
                return "INVALID";
            }

            @Override
            public ErrorType type() {
                return ErrorType.SYSTEM;
            }

            @Override
            public String defaultMessage() {
                return "系统错误";
            }

            @Override
            public boolean retryable() {
                return false;
            }

            @Override
            public Set<String> allowedDetailFields() {
                return Set.of();
            }
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> new InfrastructureException(invalid));
    }

    private enum TestError implements ErrorCode {
        QUOTA;

        @Override
        public String code() {
            return "TEST.QUOTA.0001";
        }

        @Override
        public ErrorType type() {
            return ErrorType.QUOTA;
        }

        @Override
        public String defaultMessage() {
            return "请求配额已用尽";
        }

        @Override
        public boolean retryable() {
            return false;
        }

        @Override
        public Set<String> allowedDetailFields() {
            return Set.of("limit");
        }
    }
}
