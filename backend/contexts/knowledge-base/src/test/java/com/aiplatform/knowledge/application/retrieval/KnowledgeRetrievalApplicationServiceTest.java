package com.aiplatform.knowledge.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.RetrieveKnowledgeCommand;
import com.aiplatform.knowledge.application.retrieval.result.RetrieveKnowledgeResult;
import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import com.aiplatform.knowledge.application.retrieval.service.KnowledgeAnswerGroundingApplicationService;
import com.aiplatform.knowledge.application.retrieval.service.KnowledgeRetrievalApplicationService;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.answer.KnowledgeAnswerGenerationPort;
import com.aiplatform.knowledge.domain.port.embedding.KnowledgeQueryEmbeddingPort;
import com.aiplatform.knowledge.domain.port.indexing.ActiveKnowledgeIndex;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeDocumentVectorIndexRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.retrieval.KnowledgeRetrievalCandidateRepository;
import com.aiplatform.knowledge.domain.port.retrieval.model.RetrievalSearchQuery;
import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.knowledge.domain.retrieval.policy.KnowledgeRetrievalAccessPolicy;
import com.aiplatform.knowledge.domain.retrieval.service.HybridRetrievalRanker;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeRetrievalApplicationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void retrievesHybridCandidatesAfterAclFilteringAndRrfRanking() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = answerGenerationPort("报销上限 2000 元。审批通过后 3 个工作日内打款。");
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(
                List.of(
                        candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null),
                        candidate("c2", "d2", 2, 0.88, RetrievalSignal.VECTOR, List.of("role:finance"), null)
                ),
                List.of(
                        candidate("c3", "d3", 1, 0.12, RetrievalSignal.KEYWORD, List.of("kb:7001"), null),
                        candidate("c4", "d4", 2, 0.10, RetrievalSignal.KEYWORD, List.of("role:finance"), null)
                )
        );
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(commandWithAnswerModel());

        ArgumentCaptor<KnowledgeModelRuntimeDescriptor> runtimeDescriptor = ArgumentCaptor.forClass(KnowledgeModelRuntimeDescriptor.class);
        verify(embeddingPort).embedQuery(eq(TenantId.of("2001")), runtimeDescriptor.capture(), eq("员工制度"));
        assertThat(runtimeDescriptor.getValue().providerCode()).isEqualTo("test-provider");
        assertThat(runtimeDescriptor.getValue().providerKind()).isEqualTo("openai-compatible");
        assertThat(runtimeDescriptor.getValue().modelCode()).isEqualTo("embedding-model-1");
        assertThat(runtimeDescriptor.getValue().policyVersion()).isEqualTo("test-policy-v1");
        ArgumentCaptor<RetrievalSearchQuery> vectorQuery = ArgumentCaptor.forClass(RetrievalSearchQuery.class);
        ArgumentCaptor<RetrievalSearchQuery> keywordQuery = ArgumentCaptor.forClass(RetrievalSearchQuery.class);
        verify(chunks).searchVectorCandidates(vectorQuery.capture());
        verify(chunks).searchKeywordCandidates(keywordQuery.capture());
        assertThat(vectorQuery.getValue().queryEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(keywordQuery.getValue().queryEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(vectorQuery.getValue().topK()).isEqualTo(RetrievalProfile.BALANCED.topK());
        assertThat(vectorQuery.getValue().scoreThreshold()).isEqualTo(RetrievalProfile.BALANCED.scoreThreshold());
        assertThat(vectorQuery.getValue().aclTokens())
                .contains("tenant:2001", "user:1001", "owner:1001", "kb:7001");
        assertThat(vectorQuery.getValue().visibleAt()).isEqualTo(CLOCK.instant());
        assertThat(keywordQuery.getValue().topK()).isEqualTo(RetrievalProfile.BALANCED.topK());
        assertThat(keywordQuery.getValue().scoreThreshold()).isEqualTo(RetrievalProfile.BALANCED.scoreThreshold());
        assertThat(result.answerable()).isTrue();
        assertThat(result.noAnswerReason()).isNull();
        assertThat(result.generatedAnswer()).contains("2000 元");
        assertThat(result.answerModelCode()).isEqualTo("chat-model-1");
        assertThat(result.answerGenerationReason()).isNull();
        assertThat(result.chunks()).extracting(RetrievedKnowledgeChunk::chunkId).containsExactly("c1", "c3");
        assertThat(result.chunks()).extracting(RetrievedKnowledgeChunk::signal).containsExactly(RetrievalSignal.VECTOR, RetrievalSignal.KEYWORD);
        assertThat(result.authorizationSnapshot().requestedKnowledgeBaseIds()).containsExactly("7001");
        assertThat(result.authorizationSnapshot().authorizedKnowledgeBaseIds()).containsExactly("7001");
        assertThat(result.authorizationSnapshot().roleCodes()).containsExactly("admin");
        assertThat(result.filteredCount()).isEqualTo(2);
        assertThat(result.filteredCitationCount()).isZero();
    }

    @Test
    void fallsBackToExtractiveAnswerWhenModelOutputLosesExactEvidenceFacts() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = answerGenerationPort("报销上限 5 元，审批后 3 天打款。");
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(
                List.of(candidateWithContent("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null,
                        "# 企业报销制度\n\n员工报销需要在 5 个工作日内提交发票。差旅报销上限为 2000 元。审批通过后由财务在 3 个工作日内打款。")),
                List.of()
        );
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(commandWithAnswerModel());

        assertThat(result.generatedAnswer()).contains("2000 元");
        assertThat(result.generatedAnswer()).contains("3 个工作日");
        assertThat(result.generatedAnswer()).contains("证据出处");
        assertThat(result.answerModelCode()).isEqualTo("chat-model-1");
    }

    @Test
    void usesTrustedActiveEmbeddingModelFromCommandDuringModelSwitch() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = answerGenerationPort("不需要生成答案");
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(
                List.of(candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null)),
                List.of()
        );
        List<KnowledgeModelUsageRequest> observedPolicyRequests = new ArrayList<>();
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                request -> {
                    observedPolicyRequests.add(request);
                    if (request.requiredCapability() == KnowledgeModelCapability.EMBEDDING) {
                        return KnowledgeModelUsageDecision.allow(new KnowledgeModelRuntimeDescriptor(
                                request.requestedModelConfigId(),
                                "3001",
                                "test-provider",
                                "openai-compatible",
                                runtimeConnection(),
                                request.requestedModelConfigId(),
                                7,
                                "EMBEDDING",
                                Set.of(KnowledgeModelCapability.EMBEDDING),
                                Map.of(),
                                "a".repeat(64),
                                "test-policy-v1"
                        ), Map.of("test", "true"));
                    }
                    return allowingModelPolicy().decide(request);
                },
                authorization(),
                knowledgeBases("kb-newly-configured-embedding"),
                activeIndex("active-index-embedding", "kb-7001-active"),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        service.retrieve(command());

        verify(embeddingPort).embedQuery(eq(TenantId.of("2001")), any(KnowledgeModelRuntimeDescriptor.class), eq("员工制度"));
        verify(chunks).searchVectorCandidates(any(RetrievalSearchQuery.class));
        verify(chunks).searchKeywordCandidates(any(RetrievalSearchQuery.class));
        assertThat(observedPolicyRequests)
                .extracting(KnowledgeModelUsageRequest::requestedModelConfigId)
                .contains("active-index-embedding");
    }

    @Test
    void returnsEvidenceOnlyWhenAnswerModelIsNotRequested() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = answerGenerationPort("不应被调用");
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(
                List.of(candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null)),
                List.of()
        );
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isTrue();
        assertThat(result.generatedAnswer()).isNull();
        assertThat(result.answerModelCode()).isNull();
        assertThat(result.answerGenerationReason()).contains("未指定回答模型");
        verify(answerGenerationPort, never()).generateAnswer(any(), any(), any(), any());
    }

    @Test
    void returnsEvidenceOnlyAndAuditsWhenAnswerModelPolicyIsRejected() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = answerGenerationPort("不应被调用");
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(
                List.of(candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null)),
                List.of()
        );
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                policyRejectingChat(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                auditPublisher,
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(commandWithAnswerModel());

        assertThat(result.answerable()).isTrue();
        assertThat(result.generatedAnswer()).isNull();
        assertThat(result.answerGenerationReason()).contains("模型能力不满足当前知识库场景");
        verify(answerGenerationPort, never()).generateAnswer(any(), any(), any(), any());
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("SECURITY_RAG_MODEL_POLICY_DENIED");
        assertThat(eventCaptor.getValue().detailAttributes())
                .containsEntry("reason", "模型能力不满足当前知识库场景");
    }

    @Test
    void embeddingModelPolicyDenialFailsClosedAndIsAudited() {
        KnowledgeQueryEmbeddingPort embeddingPort = mock(KnowledgeQueryEmbeddingPort.class);
        KnowledgeAnswerGenerationPort answerGenerationPort = mock(KnowledgeAnswerGenerationPort.class);
        KnowledgeRetrievalCandidateRepository chunks = mock(KnowledgeRetrievalCandidateRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                request -> KnowledgeModelUsageDecision.deny(
                        "model_policy_projection_missing",
                        "模型使用策略投影不存在或未同步"
                ),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                auditPublisher,
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.noAnswerReason()).contains("模型使用策略投影不存在");
        verifyNoInteractions(embeddingPort, chunks);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("SECURITY_RAG_MODEL_POLICY_DENIED");
    }

    @Test
    void returnsNoAnswerWhenAclFilteringRemovesAllCandidates() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = answerGenerationPort("不应被调用");
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(
                List.of(candidate("c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("role:finance"), null)),
                List.of(candidate("c2", "d2", 1, 0.20, RetrievalSignal.KEYWORD, List.of("role:finance"), null))
        );
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.noAnswerReason()).contains("未召回到具备权限");
        assertThat(result.filteredCount()).isEqualTo(2);
        assertThat(result.filteredCitationCount()).isZero();
    }

    @Test
    void filtersCrossTenantAndUnrequestedKnowledgeBaseCandidatesBeforeRanking() {
        RetrievalCandidate allowed = candidate(
                "c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null
        );
        RetrievalCandidate crossTenant = candidateWithIdentityAndContent(
                "2002", "7001", "d2", "c2", "kb-7001-active",
                2, 0.90, RetrievalSignal.VECTOR, List.of("kb:7001"), null, "跨租户内容"
        );
        RetrievalCandidate unrequestedKnowledgeBase = candidateWithIdentityAndContent(
                "2001", "7002", "d3", "c3", "kb-7002-active",
                3, 0.89, RetrievalSignal.VECTOR, List.of("kb:7001"), null, "未请求知识库内容"
        );
        RetrievalCandidate staleIndexVersion = candidateWithIdentityAndContent(
                "2001", "7001", "d4", "c4", "kb-7001-stale",
                4, 0.88, RetrievalSignal.VECTOR, List.of("kb:7001"), null, "旧索引内容"
        );
        KnowledgeRetrievalApplicationService service = serviceWithCandidates(
                List.of(allowed, crossTenant, unrequestedKnowledgeBase, staleIndexVersion),
                List.of(),
                new HybridRetrievalRanker()
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isTrue();
        assertThat(result.chunks()).extracting(RetrievedKnowledgeChunk::chunkId).containsExactly("c1");
        assertThat(result.filteredCount()).isEqualTo(3);
    }

    @Test
    void countsRepeatedForbiddenEvidenceAcrossSignalsOnlyOnce() {
        RetrievalCandidate forbiddenVector = candidate(
                "c-secret", "d-secret", 1, 0.91, RetrievalSignal.VECTOR, List.of("role:finance"), null
        );
        RetrievalCandidate forbiddenKeyword = candidate(
                "c-secret", "d-secret", 1, 0.72, RetrievalSignal.KEYWORD, List.of("role:finance"), null
        );
        KnowledgeRetrievalApplicationService service = serviceWithCandidates(
                List.of(forbiddenVector),
                List.of(forbiddenKeyword),
                new HybridRetrievalRanker()
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.filteredCount()).isEqualTo(1);
    }

    @Test
    void excludesAnEvidenceIdentityWhenAnySignalDuplicateFailsAuthorization() {
        RetrievalCandidate allowedVector = candidate(
                "c-mixed", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null
        );
        RetrievalCandidate forbiddenKeyword = candidate(
                "c-mixed", "d1", 1, 0.72, RetrievalSignal.KEYWORD, List.of("role:finance"), null
        );
        KnowledgeRetrievalApplicationService service = serviceWithCandidates(
                List.of(allowedVector),
                List.of(forbiddenKeyword),
                new HybridRetrievalRanker()
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.filteredCount()).isEqualTo(1);
    }

    @Test
    void unauthorizedKnowledgeBaseReturnsEmptyResultAndSecurityAudit() {
        KnowledgeQueryEmbeddingPort embeddingPort = mock(KnowledgeQueryEmbeddingPort.class);
        KnowledgeAnswerGenerationPort answerGenerationPort = mock(KnowledgeAnswerGenerationPort.class);
        KnowledgeRetrievalCandidateRepository chunks = mock(KnowledgeRetrievalCandidateRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        KnowledgeDocumentVectorIndexRepository vectorIndex = mock(KnowledgeDocumentVectorIndexRepository.class);
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                unauthorizedKnowledgeBases(),
                vectorIndex,
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                auditPublisher,
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.authorizationSnapshot().authorizedKnowledgeBaseIds()).isEmpty();
        assertThat(result.filteredCount()).isEqualTo(1);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("SECURITY_RAG_RETRIEVAL_DENIED");
        assertThat(eventCaptor.getValue().detailAttributes()).containsEntry("reason", "knowledge_base_not_allowed");
        verifyNoInteractions(embeddingPort, chunks);
        verifyNoInteractions(vectorIndex);
    }

    @Test
    void citationIsFilteredAgainAfterRankingAndAudited() {
        KnowledgeQueryEmbeddingPort embeddingPort = embeddingPort(new float[] {0.1f, 0.2f});
        KnowledgeAnswerGenerationPort answerGenerationPort = mock(KnowledgeAnswerGenerationPort.class);
        RetrievalCandidate authorized = candidate(
                "c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null
        );
        RetrievalCandidate injectedUnauthorized = candidate(
                "c-secret", "d-secret", 1, 0.99, RetrievalSignal.VECTOR, List.of("role:finance"), null
        );
        KnowledgeRetrievalCandidateRepository chunks = retrievalCandidates(List.of(authorized), List.of());
        HybridRetrievalRanker ranker = mock(HybridRetrievalRanker.class);
        when(ranker.rank(anyList(), anyList(), eq(RetrievalProfile.BALANCED)))
                .thenReturn(List.of(injectedUnauthorized));
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                ranker,
                new KnowledgeAnswerGroundingApplicationService(),
                auditPublisher,
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.filteredCount()).isEqualTo(1);
        assertThat(result.filteredCitationCount()).isEqualTo(1);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("SECURITY_RAG_CITATION_FILTERED");
    }

    @Test
    void mapsFreshRankerObjectsBackToCanonicalAuthorizedEvidenceByIdentity() {
        RetrievalCandidate authorized = candidateWithContent(
                "c1", "d1", 1, 0.91, RetrievalSignal.VECTOR,
                List.of("kb:7001"), null, "可信原始内容"
        );
        RetrievalCandidate freshRankerObject = candidateWithContent(
                "c1", "d1", 9, 0.99, RetrievalSignal.KEYWORD,
                List.of("kb:7001"), null, "重排器不可信替换内容"
        );
        HybridRetrievalRanker ranker = mock(HybridRetrievalRanker.class);
        when(ranker.rank(anyList(), anyList(), eq(RetrievalProfile.BALANCED)))
                .thenReturn(List.of(freshRankerObject));
        KnowledgeRetrievalApplicationService service = serviceWithCandidates(
                List.of(authorized),
                List.of(),
                ranker
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isTrue();
        assertThat(result.chunks()).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("可信原始内容");
            assertThat(chunk.score()).isEqualTo(0.91);
            assertThat(chunk.signal()).isEqualTo(RetrievalSignal.VECTOR);
        });
        assertThat(result.filteredCitationCount()).isZero();
    }

    @Test
    void rejectsRankerOutputWhoseIdentityWasNeverInAuthorizedInputs() {
        RetrievalCandidate authorized = candidate(
                "c1", "d1", 1, 0.91, RetrievalSignal.VECTOR, List.of("kb:7001"), null
        );
        RetrievalCandidate tamperedIdentity = candidateWithContent(
                "c1", "d-tampered", 1, 0.99, RetrievalSignal.VECTOR,
                List.of("kb:7001"), null, "伪造证据"
        );
        HybridRetrievalRanker ranker = mock(HybridRetrievalRanker.class);
        when(ranker.rank(anyList(), anyList(), eq(RetrievalProfile.BALANCED)))
                .thenReturn(List.of(tamperedIdentity));
        KnowledgeRetrievalApplicationService service = serviceWithCandidates(
                List.of(authorized),
                List.of(),
                ranker
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.filteredCitationCount()).isEqualTo(1);
    }

    @Test
    void missingRequestedKnowledgeBaseIsIndistinguishableFromUnauthorizedAndNeverResolvesIndex() {
        KnowledgeQueryEmbeddingPort embeddingPort = mock(KnowledgeQueryEmbeddingPort.class);
        KnowledgeAnswerGenerationPort answerGenerationPort = mock(KnowledgeAnswerGenerationPort.class);
        KnowledgeRetrievalCandidateRepository chunks = mock(KnowledgeRetrievalCandidateRepository.class);
        KnowledgeDocumentVectorIndexRepository vectorIndex = mock(KnowledgeDocumentVectorIndexRepository.class);
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                missingKnowledgeBases(),
                vectorIndex,
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        RetrieveKnowledgeResult result = service.retrieve(command());

        assertThat(result.answerable()).isFalse();
        assertThat(result.noAnswerReason()).isEqualTo("未召回到具备权限且可信的知识片段");
        assertThat(result.authorizationSnapshot().authorizedKnowledgeBaseIds()).isEmpty();
        assertThat(result.filteredCount()).isEqualTo(1);
        verify(embeddingPort, never()).embedQuery(any(), any(), any());
        verify(chunks, never()).searchVectorCandidates(any());
        verify(chunks, never()).searchKeywordCandidates(any());
        verifyNoInteractions(vectorIndex);
    }

    @Test
    void authorizedKnowledgeBaseWithoutActiveIndexFailsClosedBeforeModelOrCandidateAccess() {
        KnowledgeQueryEmbeddingPort embeddingPort = mock(KnowledgeQueryEmbeddingPort.class);
        KnowledgeAnswerGenerationPort answerGenerationPort = mock(KnowledgeAnswerGenerationPort.class);
        KnowledgeRetrievalCandidateRepository chunks = mock(KnowledgeRetrievalCandidateRepository.class);
        KnowledgeDocumentVectorIndexRepository vectorIndex = mock(KnowledgeDocumentVectorIndexRepository.class);
        when(vectorIndex.findActiveIndex(TenantId.of("2001"), "7001")).thenReturn(Optional.empty());
        KnowledgeRetrievalApplicationService service = new KnowledgeRetrievalApplicationService(
                embeddingPort,
                answerGenerationPort,
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                vectorIndex,
                chunks,
                new KnowledgeRetrievalAccessPolicy(),
                new HybridRetrievalRanker(),
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );

        assertThatThrownBy(() -> service.retrieve(command()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("知识库尚无可用索引");
        verify(vectorIndex).findActiveIndex(TenantId.of("2001"), "7001");
        verifyNoInteractions(embeddingPort, chunks);
    }

    private static RetrieveKnowledgeCommand command() {
        return command(null);
    }

    private static RetrieveKnowledgeCommand command(String answerModel) {
        return new RetrieveKnowledgeCommand(
                TenantId.of("2001"),
                "1001",
                "user",
                List.of("7001"),
                "员工制度",
                RetrievalProfile.BALANCED,
                answerModel
        );
    }

    private static RetrieveKnowledgeCommand commandWithAnswerModel() {
        return new RetrieveKnowledgeCommand(
                TenantId.of("2001"),
                "1001",
                "user",
                List.of("7001"),
                "员工制度",
                RetrievalProfile.BALANCED,
                "chat-model-1"
        );
    }

    private static RetrievalCandidate candidate(
            String chunkId,
            String documentId,
            int rank,
            double score,
            RetrievalSignal signal,
            List<String> aclTokens,
            Instant expiresAt
    ) {
        return candidateWithContent(chunkId, documentId, rank, score, signal, aclTokens, expiresAt, "员工制度内容");
    }

    private static RetrievalCandidate candidateWithContent(
            String chunkId,
            String documentId,
            int rank,
            double score,
            RetrievalSignal signal,
            List<String> aclTokens,
            Instant expiresAt,
            String content
    ) {
        return candidateWithIdentityAndContent(
                "2001",
                "7001",
                documentId,
                chunkId,
                "kb-7001-active",
                rank,
                score,
                signal,
                aclTokens,
                expiresAt,
                content
        );
    }

    private static RetrievalCandidate candidateWithIdentityAndContent(
            String tenantId,
            String knowledgeBaseId,
            String documentId,
            String chunkId,
            String indexVersion,
            int rank,
            double score,
            RetrievalSignal signal,
            List<String> aclTokens,
            Instant expiresAt,
            String content
    ) {
        return new RetrievalCandidate(
                tenantId,
                knowledgeBaseId,
                documentId,
                chunkId,
                content,
                score,
                rank,
                signal,
                new KnowledgeDocumentChunkMetadata(
                        "s3://aip-knowledge/doc.txt",
                        1,
                        "员工制度",
                        null,
                        null,
                        "internal",
                        "tenant",
                        aclTokens,
                        false,
                        Instant.parse("2026-05-20T00:00:00Z"),
                        expiresAt,
                        "embedding-model-1",
                        "heading:v1",
                        indexVersion
                )
        );
    }

    private static ModelUsagePolicyPort allowingModelPolicy() {
        return request -> {
            String modelCode = request.requiredCapability() == KnowledgeModelCapability.CHAT ? "chat-model-1" : "embedding-model-1";
            return KnowledgeModelUsageDecision.allow(new KnowledgeModelRuntimeDescriptor(
                    request.requestedModelConfigId(),
                    "3001",
                    "test-provider",
                    "openai-compatible",
                    runtimeConnection(),
                    modelCode,
                    7,
                    request.requiredCapability().name(),
                    Set.of(request.requiredCapability()),
                    Map.of(),
                    "a".repeat(64),
                    "test-policy-v1"
            ), Map.of("test", "true"));
        };
    }

    private static ModelUsagePolicyPort policyRejectingChat() {
        return request -> {
            if (request.requiredCapability() == KnowledgeModelCapability.CHAT) {
                return KnowledgeModelUsageDecision.deny("capability_not_allowed", "模型能力不满足当前知识库场景");
            }
            return allowingModelPolicy().decide(request);
        };
    }

    private static KnowledgeModelRuntimeConnectionRef runtimeConnection() {
        return new KnowledgeModelRuntimeConnectionRef(
                "https://api.test-provider.example/v1",
                "api_key",
                "secret-9001",
                "******"
        );
    }

    private static RbacAuthorizationQueryPort authorization() {
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        TenantId tenantId = TenantId.of("2001");
        when(authorization.findRoleCodes(tenantId, "user", "1001")).thenReturn(Set.of("admin"));
        when(authorization.findPermissionCodes(tenantId, "user", "1001")).thenReturn(Set.of());
        return authorization;
    }

    private static KnowledgeQueryEmbeddingPort embeddingPort(float[] embedding) {
        KnowledgeQueryEmbeddingPort embeddingPort = mock(KnowledgeQueryEmbeddingPort.class);
        when(embeddingPort.embedQuery(eq(TenantId.of("2001")), any(KnowledgeModelRuntimeDescriptor.class), eq("员工制度")))
                .thenReturn(embedding.clone());
        return embeddingPort;
    }

    private static KnowledgeAnswerGenerationPort answerGenerationPort(String answer) {
        KnowledgeAnswerGenerationPort answerGenerationPort = mock(KnowledgeAnswerGenerationPort.class);
        when(answerGenerationPort.generateAnswer(eq(TenantId.of("2001")), any(KnowledgeModelRuntimeDescriptor.class), eq("员工制度"), any()))
                .thenReturn(answer);
        return answerGenerationPort;
    }

    private static KnowledgeRetrievalCandidateRepository retrievalCandidates(
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates
    ) {
        KnowledgeRetrievalCandidateRepository chunks = mock(KnowledgeRetrievalCandidateRepository.class);
        when(chunks.searchVectorCandidates(any(RetrievalSearchQuery.class))).thenReturn(vectorCandidates);
        when(chunks.searchKeywordCandidates(any(RetrievalSearchQuery.class))).thenReturn(keywordCandidates);
        return chunks;
    }

    private static KnowledgeRetrievalApplicationService serviceWithCandidates(
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates,
            HybridRetrievalRanker ranker
    ) {
        return new KnowledgeRetrievalApplicationService(
                embeddingPort(new float[] {0.1f, 0.2f}),
                mock(KnowledgeAnswerGenerationPort.class),
                allowingModelPolicy(),
                authorization(),
                knowledgeBases(),
                activeIndex(),
                retrievalCandidates(vectorCandidates, keywordCandidates),
                new KnowledgeRetrievalAccessPolicy(),
                ranker,
                new KnowledgeAnswerGroundingApplicationService(),
                mock(AuditEventPublisher.class),
                CLOCK
        );
    }

    private static KnowledgeBaseRepository knowledgeBases() {
        return knowledgeBases("embedding-model-1");
    }

    private static KnowledgeBaseRepository knowledgeBases(String mountedEmbeddingModel) {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(Optional.of(knowledgeBase(mountedEmbeddingModel)));
        return repository;
    }

    private static KnowledgeDocumentVectorIndexRepository activeIndex() {
        return activeIndex("embedding-model-1", "kb-7001-active");
    }

    private static KnowledgeDocumentVectorIndexRepository activeIndex(String embeddingModel, String indexVersion) {
        KnowledgeDocumentVectorIndexRepository repository = mock(KnowledgeDocumentVectorIndexRepository.class);
        when(repository.findActiveIndex(TenantId.of("2001"), "7001"))
                .thenReturn(Optional.of(new ActiveKnowledgeIndex(embeddingModel, indexVersion)));
        return repository;
    }

    private static KnowledgeBaseRepository missingKnowledgeBases() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(Optional.empty());
        return repository;
    }

    private static KnowledgeBaseRepository unauthorizedKnowledgeBases() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        when(repository.findById(TenantId.of("2001"), "7001")).thenReturn(Optional.of(new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "财务私有知识库",
                "",
                KnowledgeVisibility.PRIVATE,
                Set.of("2002"),
                "2002",
                "embedding-model-1",
                true,
                true,
                Instant.parse("2026-05-20T00:00:00Z"),
                Instant.parse("2026-05-20T00:00:00Z")
        )));
        return repository;
    }

    private static KnowledgeBase knowledgeBase() {
        return knowledgeBase("embedding-model-1");
    }

    private static KnowledgeBase knowledgeBase(String mountedEmbeddingModel) {
        return new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "员工制度库",
                "",
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                mountedEmbeddingModel,
                true,
                true,
                Instant.parse("2026-05-20T00:00:00Z"),
                Instant.parse("2026-05-20T00:00:00Z")
        );
    }
}
