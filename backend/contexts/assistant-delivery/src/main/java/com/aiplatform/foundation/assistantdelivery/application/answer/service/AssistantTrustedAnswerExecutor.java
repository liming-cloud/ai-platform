package com.aiplatform.foundation.assistantdelivery.application.answer.service;

import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerCitation;
import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerResult;
import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerTarget;
import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerUsage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateRetriever;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.modelruntime.application.configuration.dto.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 以冻结助手目标执行嵌入、受权检索、证据选择、对话和引用核验的唯一应用门面。
 *
 * <p>它不创建运行聚合、不占用配额、不写事件或投影；调用方必须在异常后将已经占额的运行关闭。</p>
 */
public final class AssistantTrustedAnswerExecutor {

    private final ModelRuntimeResolver models;
    private final UsableModelConfigurationLookup modelConfigurations;
    private final GovernedEmbeddingRuntime governedEmbeddings;
    private final GovernedChatRuntime governedChats;
    private final IndexCandidateRetriever candidates;

    /**
     * 创建仅用于历史发布部署兼容路径的执行门面。
     */
    public AssistantTrustedAnswerExecutor(
            ModelRuntimeResolver models,
            IndexCandidateRetriever candidates) {
        this(models, null, null, null, candidates);
    }

    public AssistantTrustedAnswerExecutor(
            ModelRuntimeResolver models,
            UsableModelConfigurationLookup modelConfigurations,
            GovernedEmbeddingRuntime governedEmbeddings,
            GovernedChatRuntime governedChats,
            IndexCandidateRetriever candidates) {
        this.models = Objects.requireNonNull(models, "模型运行时解析器不能为空");
        this.modelConfigurations = modelConfigurations;
        this.governedEmbeddings = governedEmbeddings;
        this.governedChats = governedChats;
        this.candidates = Objects.requireNonNull(candidates, "候选检索器不能为空");
    }

    /**
     * 执行本次可信回答，不持久化问题、回答或候选正文。
     *
     * @throws IllegalArgumentException 问题、模型快照或运行时返回不符合冻结目标
     */
    public AssistantTrustedAnswerResult execute(
            TrustedRequestContext context,
            AssistantTrustedAnswerTarget target,
            String question) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(target, "可信回答目标不能为空");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空白");
        }
        var chatRevision = target.usesGovernedModelConfigurations()
                ? target.governedChat().configurationRevision()
                : target.chatDeploymentRevision();
        var embeddingRevision = target.usesGovernedModelConfigurations()
                ? target.governedEmbedding().configurationRevision()
                : target.embeddingDeploymentRevision();
        Integer candidateCount = null;
        try {
            var embedding = embed(context, target, question);
            var retrieved = candidates.retrieve(new IndexCandidateQuery(
                    context.tenantId(), target.knowledgeSpaceId(), target.indexVersionId(),
                    target.usesGovernedModelConfigurations()
                            ? EmbeddingConfiguration.governed(
                                    target.governedEmbedding().configurationId(),
                                    target.governedEmbedding().configurationRevision(),
                                    target.governedEmbedding().catalogEntryId(),
                                    target.governedEmbedding().catalogEntryRevision(),
                                    target.embeddingDimension())
                            : new EmbeddingConfiguration(
                                    target.embeddingDeploymentId(), target.embeddingDeploymentRevision(),
                                    target.embeddingDimension()),
                    embedding, TrustedAnswerEvidenceSelector.candidateLimit(target.retrievalLimit())));
            candidateCount = retrieved.size();
            var evidence = TrustedAnswerEvidenceSelector.select(
                    question, retrieved, target.retrievalLimit(), target.minimumScore());
            if (evidence.isEmpty()) {
                return AssistantTrustedAnswerResult.refused(
                        candidateCount, chatRevision, embeddingRevision);
            }
            return answer(context, target, question, evidence, candidateCount, chatRevision, embeddingRevision);
        } catch (AssistantTrustedAnswerExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AssistantTrustedAnswerExecutionException(
                    new com.aiplatform.foundation.assistantdelivery.application.answer.dto
                            .AssistantTrustedAnswerExecutionObservation(
                                    candidateCount, chatRevision, embeddingRevision),
                    exception);
        }
    }

    private float[] embed(
            TrustedRequestContext context,
            AssistantTrustedAnswerTarget target,
            String question) {
        var reference = target.usesGovernedModelConfigurations()
                ? new EmbeddingDeploymentRef(
                        target.governedEmbedding().configurationId(),
                        target.governedEmbedding().configurationRevision(), target.embeddingDimension())
                : new EmbeddingDeploymentRef(
                        target.embeddingDeploymentId(), target.embeddingDeploymentRevision(), target.embeddingDimension());
        var request = new EmbeddingRequest(reference, List.of(question));
        var result = target.usesGovernedModelConfigurations()
                ? requireGovernedEmbeddings().embed(context, requireEmbeddingConfiguration(context, target), request)
                : models.resolveEmbedding(context.tenantId(), target.embeddingDeploymentId()).embed(request);
        if (!reference.equals(result.deployment())
                || result.vectors().size() != 1
                || result.vectors().getFirst().index() != 0) {
            throw new IllegalStateException("查询嵌入结果不符合助手冻结模型");
        }
        return result.vectors().getFirst().values();
    }

    private AssistantTrustedAnswerResult answer(
            TrustedRequestContext context,
            AssistantTrustedAnswerTarget target,
            String question,
            List<IndexCandidate> evidence,
            int candidateCount,
            long chatRevision,
            long embeddingRevision) {
        var deployment = target.usesGovernedModelConfigurations()
                ? new ChatDeploymentRef(target.governedChat().configurationId(), target.governedChat().configurationRevision())
                : new ChatDeploymentRef(target.chatDeploymentId(), target.chatDeploymentRevision());
        var request = new ChatRequest(
                deployment, TrustedAnswerPromptBuilder.build(target, question, evidence), target.maxOutputTokens());
        var result = target.usesGovernedModelConfigurations()
                ? requireGovernedChats().chat(context, requireChatConfiguration(context, target), request)
                : models.resolveChat(context.tenantId(), target.chatDeploymentId()).chat(request);
        if (!deployment.equals(result.deployment())) {
            throw new IllegalStateException("对话结果不符合助手冻结模型");
        }
        var citations = IntStream.range(0, evidence.size())
                .mapToObj(index -> citation(index + 1, evidence.get(index)))
                .toList();
        var answer = requireGeneratedAnswer(result.content())
                + "\n\n引用："
                + IntStream.rangeClosed(1, citations.size())
                        .mapToObj(index -> "[" + index + "]")
                        .reduce((left, right) -> left + " " + right)
                        .orElseThrow();
        return new AssistantTrustedAnswerResult(
                AssistantTrustedAnswerResult.Outcome.ANSWERED, answer, citations,
                new AssistantTrustedAnswerUsage(
                        result.usage().inputTokens(), result.usage().outputTokens(), result.usage().totalTokens()),
                candidateCount, chatRevision, embeddingRevision);
    }

    private UsableModelConfigurationSnapshot requireEmbeddingConfiguration(
            TrustedRequestContext context, AssistantTrustedAnswerTarget target) {
        var binding = target.governedEmbedding();
        return requireConfiguration(context, binding, ModelType.EMBEDDING);
    }

    private UsableModelConfigurationSnapshot requireChatConfiguration(
            TrustedRequestContext context, AssistantTrustedAnswerTarget target) {
        var binding = target.governedChat();
        return requireModelConfigurations().find(context, new ModelConfigurationId(binding.configurationId()), ModelType.CHAT)
                .filter(value -> value.configurationRevision() == binding.configurationRevision()
                        && value.catalogEntryId().value() == binding.catalogEntryId()
                        && value.catalogEntryRevision() == binding.catalogEntryRevision()
                        && value.capabilities().outputLimit() >= target.maxOutputTokens())
                .orElseThrow(() -> new IllegalStateException("助手冻结的受控对话模型配置不可用"));
    }

    private UsableModelConfigurationSnapshot requireConfiguration(
            TrustedRequestContext context, GovernedEmbeddingModelBinding binding, ModelType type) {
        return requireModelConfigurations().find(context, new ModelConfigurationId(binding.configurationId()), type)
                .filter(value -> value.configurationRevision() == binding.configurationRevision()
                        && value.catalogEntryId().value() == binding.catalogEntryId()
                        && value.catalogEntryRevision() == binding.catalogEntryRevision()
                        && value.capabilities().embeddingDimension() == binding.dimension())
                .orElseThrow(() -> new IllegalStateException("助手冻结的受控嵌入模型配置不可用"));
    }

    private UsableModelConfigurationLookup requireModelConfigurations() {
        if (modelConfigurations == null || governedEmbeddings == null || governedChats == null) {
            throw new IllegalStateException("受控模型配置运行时不可用");
        }
        return modelConfigurations;
    }

    private GovernedEmbeddingRuntime requireGovernedEmbeddings() {
        if (governedEmbeddings == null) {
            throw new IllegalStateException("受控嵌入运行时不可用");
        }
        return governedEmbeddings;
    }

    private GovernedChatRuntime requireGovernedChats() {
        if (governedChats == null) {
            throw new IllegalStateException("受控对话运行时不可用");
        }
        return governedChats;
    }

    private static AssistantTrustedAnswerCitation citation(int sequence, IndexCandidate candidate) {
        return new AssistantTrustedAnswerCitation(
                sequence, candidate.dataSourceId(), candidate.contentVersion(), candidate.chunkId(),
                candidate.ordinal(), TrustedAnswerPromptBuilder.quote(candidate.content()), candidate.score());
    }

    private static String requireGeneratedAnswer(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("模型没有返回可引用回答");
        }
        return value;
    }
}
