package com.aiplatform.foundation.assistantdelivery.application.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.assistant.command.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.PublishAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantReferenceUnavailableException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantPage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublicationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantPage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.dto.ActiveIndexSnapshot;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.PublishedChatDeploymentSnapshot;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantServiceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final long ASSISTANT_ID = 410000000000000001L;
    private static final long VERSION_ID = 420000000000000001L;
    private static final long CHAT_ID = 320000000000000001L;
    private static final long SPACE_ID = 210000000000000001L;
    private static final long INDEX_ID = 230000000000000001L;
    private static final long EMBEDDING_ID = 320000000000000002L;
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void 应从已发布模型和活动索引创建并发布冻结版本() {
        var repository = new MemoryRepository();
        var service = service(repository, true, true);
        var assistant = service.create(new CreateAssistantCommand(
                context("assistant:write"),
                "assistant-create-0001",
                "门店导购助手"));

        var created = service.createVersion(versionCommand(assistant.assistant().id()));
        var published = service.publishVersion(new PublishAssistantVersionCommand(
                context("assistant:write"),
                "assistant-publish-0001",
                created.assistantVersion().id(),
                0));

        assertEquals(Long.toString(ASSISTANT_ID), assistant.assistant().id());
        assertEquals(Long.toString(VERSION_ID), created.assistantVersion().id());
        assertEquals(9, created.assistantVersion().chatDeploymentRevision());
        assertEquals(4, created.assistantVersion().indexVersionRevision());
        assertEquals(3, created.assistantVersion().embeddingDeploymentRevision());
        assertEquals("PUBLISHED", published.assistantVersion().status());
        assertEquals(1, published.assistantVersion().version());
        assertTrue(repository.findVersion(
                        TENANT,
                        new AssistantVersionId(VERSION_ID))
                .orElseThrow()
                .publishedAt()
                .equals(NOW));
    }

    @Test
    void 模型未发布或知识空间没有活动索引时必须拒绝创建版本() {
        var repository = new MemoryRepository();
        var noModel = service(repository, false, true);
        noModel.create(new CreateAssistantCommand(
                context("assistant:write"),
                "assistant-create-0002",
                "校园问答助手"));

        assertThrows(
                AssistantReferenceUnavailableException.class,
                () -> noModel.createVersion(versionCommand(Long.toString(ASSISTANT_ID))));

        var noIndex = service(repository, true, false);
        assertThrows(
                AssistantReferenceUnavailableException.class,
                () -> noIndex.createVersion(versionCommand(Long.toString(ASSISTANT_ID))));
    }

    @Test
    void 缺少助手写权限时不得产生任何事实() {
        var repository = new MemoryRepository();
        var service = service(repository, true, true);

        assertThrows(
                AssistantPermissionDeniedException.class,
                () -> service.create(new CreateAssistantCommand(
                        context("assistant:read"),
                        "assistant-create-0003",
                        "无权助手")));
        assertTrue(repository.assistant == null);
    }

    private static AssistantService service(
            MemoryRepository repository,
            boolean modelAvailable,
            boolean indexAvailable) {
        PublishedChatDeploymentLookup chats = (tenantId, deploymentId) ->
                modelAvailable
                        ? Optional.of(new PublishedChatDeploymentSnapshot(
                                CHAT_ID,
                                9,
                                8192,
                                1024))
                        : Optional.empty();
        ActiveIndexLookup indexes = (tenantId, knowledgeSpaceId) ->
                indexAvailable
                        ? Optional.of(new ActiveIndexSnapshot(
                                SPACE_ID,
                                INDEX_ID,
                                4,
                                EMBEDDING_ID,
                                3,
                                8))
                        : Optional.empty();
        var identifiers = new ArrayDeque<>(List.of(ASSISTANT_ID, VERSION_ID));
        return new AssistantService(
                repository,
                chats,
                indexes,
                Clock.fixed(NOW, ZoneOffset.UTC),
                identifiers::removeFirst,
                5,
                20);
    }

    private static CreateAssistantVersionCommand versionCommand(String assistantId) {
        return new CreateAssistantVersionCommand(
                context("assistant:write"),
                "assistant-version-0001",
                assistantId,
                Long.toString(CHAT_ID),
                Long.toString(SPACE_ID),
                "只依据编号证据回答，并在结论后标注引用。",
                512,
                5,
                0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE);
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(3687303715884105730L),
                new MembershipId(2148216680369618946L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-assistant-0001");
    }

    private static final class MemoryRepository implements AssistantRepository {

        private Assistant assistant;
        private AssistantVersion assistantVersion;

        @Override
        public AssistantCreationOutcome create(
                AssistantCreation creation,
                int tenantLimit) {
            assistant = creation.assistant();
            return new AssistantCreationOutcome(assistant, false);
        }

        @Override
        public Optional<Assistant> findAssistant(
                TenantId tenantId,
                AssistantId assistantId) {
            return assistant != null
                            && assistant.tenantId().equals(tenantId)
                            && assistant.id().equals(assistantId)
                    ? Optional.of(assistant)
                    : Optional.empty();
        }

        @Override
        public AssistantPage findPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            return new AssistantPage(
                    assistant == null ? List.of() : List.of(assistant),
                    assistant == null ? 0L : 1L);
        }

        @Override
        public PublishedAssistantPage findPublishedPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            return new PublishedAssistantPage(List.of(), 0L);
        }

        @Override
        public AssistantVersionCreationOutcome createVersion(
                AssistantVersionCreation creation,
                int perAssistantLimit) {
            assistant = creation.advancedAssistant();
            assistantVersion = creation.assistantVersion();
            return new AssistantVersionCreationOutcome(assistantVersion, false);
        }

        @Override
        public Optional<AssistantVersion> findVersion(
                TenantId tenantId,
                AssistantVersionId assistantVersionId) {
            return assistantVersion != null
                            && assistantVersion.tenantId().equals(tenantId)
                            && assistantVersion.id().equals(assistantVersionId)
                    ? Optional.of(assistantVersion)
                    : Optional.empty();
        }

        @Override
        public Optional<AssistantVersionPublicationOutcome> replayPublication(
                TenantId tenantId,
                AssistantVersionId assistantVersionId,
                String idempotencyKey,
                String requestFingerprint) {
            return Optional.empty();
        }

        @Override
        public AssistantVersionPublicationOutcome publishVersion(
                AssistantVersionPublication publication) {
            assistantVersion = publication.publishedVersion();
            return new AssistantVersionPublicationOutcome(assistantVersion, false);
        }
    }
}
