package com.aiplatform.foundation.security.secret;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * 可在使用结束后主动清零的短生命周期秘密。
 */
public final class SecretValue implements AutoCloseable {

    private static final String REDACTED = "[已脱敏]";

    private final char[] value;
    private boolean closed;

    private SecretValue(char[] value) {
        this.value = value;
    }

    public static SecretValue copyOf(char[] source) {
        Objects.requireNonNull(source, "密钥内容不能为空");
        if (source.length == 0) {
            throw new IllegalArgumentException("密钥内容不能为空");
        }
        return new SecretValue(Arrays.copyOf(source, source.length));
    }

    /**
     * 使用秘密的临时副本，并在回调结束后清零该副本。
     *
     * @param action 仅在当前调用内消费字符数组的动作
     * @param <T> 动作返回类型
     * @return 动作结果
     */
    public <T> T use(Function<char[], T> action) {
        Objects.requireNonNull(action, "密钥使用动作不能为空");
        char[] temporary;
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("密钥内容已经关闭");
            }
            temporary = Arrays.copyOf(value, value.length);
        }
        try {
            return action.apply(temporary);
        } finally {
            Arrays.fill(temporary, '\0');
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            Arrays.fill(value, '\0');
            closed = true;
        }
    }

    @Override
    public String toString() {
        return REDACTED;
    }
}
