package org.fujay.ai.platform.shared.error;

public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;

    public PlatformException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
