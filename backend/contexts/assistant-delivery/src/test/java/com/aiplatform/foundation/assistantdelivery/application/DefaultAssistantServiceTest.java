package com.aiplatform.foundation.assistantdelivery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.api.model.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.PublishAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantSlice;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantVersionPublicationOutcome;
import com.aiplatform.foundation.assistantdelivery.application.port.out.PublishedAssistantSlice;
import com.aiplatform.foundation.assistantdelivery.domain.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.api.model.ActiveIndexSnapshot;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishedChatDeploymentSnapshot;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultAssistantServiceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final UUID ASSISTANT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID CHAT_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID INDEX_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID EMBEDDING_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000001");
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

        assertEquals(ASSISTANT_ID, assistant.assistant().id());
        assertEquals(VERSION_ID, created.assistantVersion().id());
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
                () -> noModel.createVersion(versionCommand(ASSISTANT_ID)));

        var noIndex = service(repository, true, false);
        assertThrows(
                AssistantReferenceUnavailableException.class,
                () -> noIndex.createVersion(versionCommand(ASSISTANT_ID)));
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

    private static DefaultAssistantService service(
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
        return new DefaultAssistantService(
                repository,
                chats,
                indexes,
                Clock.fixed(NOW, ZoneOffset.UTC),
                identifiers::removeFirst,
                5,
                20);
    }

    private static CreateAssistantVersionCommand versionCommand(UUID assistantId) {
        return new CreateAssistantVersionCommand(
                context("assistant:write"),
                "assistant-version-0001",
                assistantId,
                CHAT_ID,
                SPACE_ID,
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
        public AssistantSlice findPage(TenantId tenantId, String cursor, int limit) {
            return new AssistantSlice(
                    assistant == null ? List.of() : List.of(assistant),
                    null);
        }

        @Override
        public PublishedAssistantSlice findPublishedPage(
                TenantId tenantId,
                String cursor,
                int limit) {
            return new PublishedAssistantSlice(List.of(), null);
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
