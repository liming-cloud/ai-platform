package com.aiplatform.foundation.security.egress;

/**
 * 目标地址违反平台出站访问策略。
 */
public final class UnsafeOutboundEndpointException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public UnsafeOutboundEndpointException(String message) {
        super(message);
    }

    public UnsafeOutboundEndpointException(String message, Throwable cause) {
        super(message, cause);
    }
}
