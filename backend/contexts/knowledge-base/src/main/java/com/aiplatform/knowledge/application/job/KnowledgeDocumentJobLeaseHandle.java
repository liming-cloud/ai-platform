package com.aiplatform.knowledge.application.job;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次 claim 的 heartbeat 生命周期句柄。 */
public final class KnowledgeDocumentJobLeaseHandle implements AutoCloseable {
    private final AtomicBoolean closed;
    private final ScheduledFuture<?> future;

    KnowledgeDocumentJobLeaseHandle(AtomicBoolean closed, ScheduledFuture<?> future) {
        this.closed = Objects.requireNonNull(closed, "续租关闭标记不能为空");
        this.future = Objects.requireNonNull(future, "续租 future 不能为空");
    }

    @Override
    public void close() {
        closed.set(true);
        future.cancel(false);
    }
}
