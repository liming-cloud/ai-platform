package com.aiplatform.foundation.platform.web.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * 集中维护框架无关错误类别到 HTTP 状态的唯一映射。
 */
final class HttpErrorTypeMapping {

    private HttpErrorTypeMapping() {
    }

    static HttpStatus status(ErrorType type) {
        return switch (Objects.requireNonNull(type, "错误类别不能为空")) {
            case REQUEST -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION -> HttpStatus.UNAUTHORIZED;
            case AUTHORIZATION -> HttpStatus.FORBIDDEN;
            case RESOURCE -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case QUOTA -> HttpStatus.TOO_MANY_REQUESTS;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEPENDENCY_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case SYSTEM -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
