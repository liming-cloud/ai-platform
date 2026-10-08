package com.aiplatform.foundation.platform.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class FoundationExceptionHandlerTest {

    private final FoundationExceptionHandler handler =
            new FoundationExceptionHandler(() -> 1001L);

    @ParameterizedTest
    @CsvSource({
        "foundation, 429, TEST.QUOTA.0001",
        "invalid, 400, COMMON.REQUEST.0001",
        "missing, 404, COMMON.RESOURCE.0001",
        "unexpected, 500, COMMON.SYSTEM.0001"
    })
    void SSE拒绝仍返回统一JSON错误而不因媒体协商变为服务故障(String failure, int expectedStatus, String expectedCode) throws Exception {
        var mvc = MockMvcBuilders
                .standaloneSetup(new SseProbeController()).setControllerAdvice(handler).build();

        var result = mvc.perform(get("/test-sse/{failure}", failure).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.details").isEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("内部");
    }

    @RestController
    private static final class SseProbeController {
        /** 在写出 SSE 首帧前拒绝请求，检验统一错误契约。 */
        @GetMapping(value = "/test-sse/{failure}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter stream(@PathVariable String failure) throws NoResourceFoundException {
            switch (failure) {
                case "foundation" -> throw new ApplicationException(TestError.QUOTA);
                case "invalid" -> throw new IllegalArgumentException("内部校验原文");
                case "missing" -> throw new NoResourceFoundException(HttpMethod.GET, "test-sse", "");
                default -> throw new IllegalStateException("内部失败原文");
            }
        }
    }

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
