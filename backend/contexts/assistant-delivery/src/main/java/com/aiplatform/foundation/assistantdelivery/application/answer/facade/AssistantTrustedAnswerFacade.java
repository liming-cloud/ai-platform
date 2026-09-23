package com.aiplatform.foundation.assistantdelivery.application.answer.facade;

import com.aiplatform.foundation.assistantdelivery.application.answer.service.AssistantTrustedAnswerExecutor;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;

/** 向查询上下文公开可信回答执行；助手上下文保留检索与提示组装的内部实现。 */
public final class AssistantTrustedAnswerFacade {
    private final AssistantTrustedAnswerExecutor delegate;

    /** 以公开模型与候选端口组装助手上下文拥有的可信回答执行。 */
    public AssistantTrustedAnswerFacade(
            ModelRuntimeResolver models,
            UsableModelConfigurationLookup configurations,
            GovernedEmbeddingRuntime embeddings,
            GovernedChatRuntime chats,
            ChatRouteExecutor routes,
            IndexCandidateRetriever candidates) {
        delegate = new AssistantTrustedAnswerExecutor(
                models, configurations, embeddings, chats, routes, candidates);
    }

    /** 执行可信回答，失败时保留已确认的观测事实。 */
    public AssistantTrustedAnswerResult execute(
            TrustedRequestContext context, AssistantTrustedAnswerTarget target, String question) {
        return delegate.execute(context, target, question);
    }
}
