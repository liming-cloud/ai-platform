package com.aiplatform.appstudio.domain.port.debug;

import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;

/**
 * 调试运行快照端口：持久化 assistant message 对应的 evidence 历史事实。
 */
public interface AppStudioDebugRunSnapshotPort {
    void record(AppStudioDebugRunSnapshot snapshot);
}
