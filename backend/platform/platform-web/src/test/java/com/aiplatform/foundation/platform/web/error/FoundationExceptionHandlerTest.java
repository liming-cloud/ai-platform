package com.aiplatform.foundation.platform.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class FoundationExceptionHandlerTest {

    private final FoundationExceptionHandler handler =
            new FoundationExceptionHandler(() -> 1001L);

    @Test
    void 按错误类别映射状态并只返回安全详情() {
        var response = handler.foundationException(new ApplicationException(
                TestError.QUOTA,
                Map.of("limit", 20, "sql", "不得返回")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("TEST.QUOTA.0001");
        assertThat(response.getBody().details()).isEqualTo(Map.of("limit", 20));
        assertThat(response.getBody().traceId()).isEqualTo("trc_1001");
    }

    @Test
    void 未识别异常不返回原始消息和类型() {
        var response = handler.unrecognized(
                new IllegalStateException("仅服务端可见的内部失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON.SYSTEM.0001");
        assertThat(response.getBody().message()).isEqualTo("系统暂时无法处理请求");
        assertThat(response.getBody().details()).isEmpty();
    }

    @Test
    void 未匹配路由应返回不泄漏内部实现的资源不存在响应() {
        var response = handler.unrecognized(new NoResourceFoundException(
                HttpMethod.GET,
                "api/v1/model-configurations",
                ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON.RESOURCE.0001");
        assertThat(response.getBody().message()).isEqualTo("请求的资源不存在");
        assertThat(response.getBody().details()).isEmpty();
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
