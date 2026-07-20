package com.aiplatform.shared.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ResponseHeader {
    private final String code;
    private final String message;
    private final String traceId;

    public ResponseHeader(String code, String message, String traceId) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
    }

    @JsonProperty("code")
    public String code() {
        return code;
    }

    @JsonProperty("message")
    public String message() {
        return message;
    }

    @JsonProperty("traceId")
    public String traceId() {
        return traceId;
    }
}
