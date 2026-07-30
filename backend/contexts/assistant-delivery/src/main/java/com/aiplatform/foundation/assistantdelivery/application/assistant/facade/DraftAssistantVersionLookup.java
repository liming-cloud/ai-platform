package com.aiplatform.foundation.assistantdelivery.application.assistant.facade;

import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Optional;

/**
 * 向草稿测试路径提供受权限约束的助手草稿执行快照。
 *
 * <p>调用方必须传入由令牌恢复的可信上下文；该门面不返回已发布版本，也不能替代普通查询的
 * {@link PublishedAssistantVersionLookup}。</p>
 */
public interface DraftAssistantVersionLookup {

    /**
     * 查找当前主体可测试的同租户草稿版本。
     *
     * @param context 令牌恢复的可信请求上下文
     * @param assistantVersionId 助手版本标识
     * @return 仅在版本为 {@code DRAFT} 时返回冻结快照
     * @throws com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException
     *         当前主体没有 {@code assistant:write} 权限
     */
    Optional<DraftAssistantVersionSnapshot> find(
            TrustedRequestContext context,
            long assistantVersionId);
}
