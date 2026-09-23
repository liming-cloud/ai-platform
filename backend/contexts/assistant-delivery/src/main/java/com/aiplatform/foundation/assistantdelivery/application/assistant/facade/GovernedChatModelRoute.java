package com.aiplatform.foundation.assistantdelivery.application.assistant.facade;

import java.util.List;

/**
 * 助手版本冻结的有序聊天候选；主候选在首位，最多允许两个受控后备候选。
 *
 * @throws IllegalArgumentException 候选为空、含空值、重复或超过受控数量时拒绝。
 */
public record GovernedChatModelRoute(List<GovernedChatModelBinding> candidates) {

    public GovernedChatModelRoute {
        if (candidates == null
                || candidates.isEmpty()
                || candidates.size() > 3
                || candidates.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("聊天路由候选必须为一到三个完整绑定");
        }
        candidates = List.copyOf(candidates);
        if (candidates.stream().distinct().count() != candidates.size()) {
            throw new IllegalArgumentException("聊天路由候选不能重复");
        }
    }

    /**
     * 返回冻结路由的主候选。
     */
    public GovernedChatModelBinding primary() {
        return candidates.getFirst();
    }
}
