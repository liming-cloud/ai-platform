package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

/** 冻结评测决定不能授权当前助手草稿修订时抛出。 */
public final class AssistantPublicationDeniedException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public AssistantPublicationDeniedException() {
        super("发布决定不允许当前助手版本发布");
    }
}
