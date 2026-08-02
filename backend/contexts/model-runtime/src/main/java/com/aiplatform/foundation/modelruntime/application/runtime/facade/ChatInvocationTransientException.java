package com.aiplatform.foundation.modelruntime.application.runtime.facade;

/**
 * 供应商在产生任何对话输出前发生的可重试调用失败。
 *
 * <p>仅该异常允许冻结聊天路由尝试下一个候选；配置失效、协议校验失败和已产生输出的失败不得转换为此类型。</p>
 */
public final class ChatInvocationTransientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean beforeOutput;

    private ChatInvocationTransientException(Throwable cause) {
        super("对话模型在产生输出前暂时不可用", cause);
        beforeOutput = true;
    }

    /**
     * 将供应商同步调用失败标记为尚未产生输出的瞬态失败。
     */
    public static ChatInvocationTransientException beforeOutput(Throwable cause) {
        return new ChatInvocationTransientException(cause);
    }

    public boolean beforeOutput() {
        return beforeOutput;
    }
}
