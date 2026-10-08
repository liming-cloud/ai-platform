package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantIdempotencyConflictException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CreatorId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelRoute;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantRequestMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantVersionMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantVersionChatRouteCandidateMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = PostgresAssistantRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresAssistantRepositoryTest {

    private static final TenantId STORE =
            new TenantId(100000000000000001L);
    private static final TenantId CAMPUS =
            new TenantId(100000000000000002L);
    private static final CreatorId CREATOR = new CreatorId(3382738893456539650L);
    private static final long CHAT = 320000000000000001L;
    private static final long CHAT_VERIFICATION = 330000000000000001L;
    private static final long SPACE = 210000000000000001L;
    private static final long INDEX = 230000000000000001L;
    private static final long EMBEDDING = 320000000000000002L;
    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");
    private static final AtomicLong IDENTIFIERS =
            new AtomicLong(410000000000000000L);

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private AssistantRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("foundation.persistence.snowflake.worker-id", () -> "1");
        registry.add("foundation.persistence.snowflake.datacenter-id", () -> "1");
        registry.add(
                "spring.flyway.locations",
                () -> String.join(
                        ",",
                        "classpath:db/migration/knowledge-data",
                        "classpath:db/migration/model-runtime",
                        "classpath:db/migration/assistant-delivery"));
    }

    @BeforeEach
    void clearAndSeedReferences() {
        jdbc.update("DELETE FROM assistant_delivery.assistant_version_chat_route_candidate");
        jdbc.update("DELETE FROM assistant_delivery.assistant_request");
        jdbc.update("DELETE FROM assistant_delivery.assistant_version");
        jdbc.update("DELETE FROM assistant_delivery.assistant");
        jdbc.update("DELETE FROM model_runtime.model_deployment_request");
        jdbc.update("""
                UPDATE model_runtime.model_deployment
                SET status = 'DRAFT', last_verification_id = NULL, verification_expires_at = NULL
                WHERE last_verification_id IS NOT NULL
                """);
        jdbc.update("DELETE FROM model_runtime.model_capability_verification");
        jdbc.update("DELETE FROM model_runtime.model_deployment");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        jdbc.update("DELETE FROM knowledge_data.index_build_job");
        jdbc.update("DELETE FROM knowledge_data.index_version_source");
        jdbc.update("DELETE FROM knowledge_data.index_version_request");
        jdbc.update("DELETE FROM knowledge_data.index_version");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        seedReferences();
    }

    @Test
    void 助手和版本创建支持幂等重放并隔离租户() {
        var assistant = assistant(STORE, "门店导购助手");
        var first = repository.create(
                new AssistantCreation(assistant, "assistant-key-0001", "a".repeat(64)),
                5);
        var replayed = repository.create(
                new AssistantCreation(assistant, "assistant-key-0001", "a".repeat(64)),
                5);

        assertFalse(first.replayed());
        assertTrue(replayed.replayed());
        assertTrue(repository.findAssistant(STORE, assistant.id()).isPresent());
        assertTrue(repository.findAssistant(CAMPUS, assistant.id()).isEmpty());

        var version = version(assistant.advanceVersion());
        var created = repository.createVersion(
                new AssistantVersionCreation(
                        assistant,
                        assistant.advanceVersion(),
                        version,
                        "version-key-0001",
                        "b".repeat(64)),
                20);
        var versionReplay = repository.createVersion(
                new AssistantVersionCreation(
                        assistant,
                        assistant.advanceVersion(),
                        version,
                        "version-key-0001",
                        "b".repeat(64)),
                20);

        assertFalse(created.replayed());
        assertTrue(versionReplay.replayed());
        assertEquals(1, created.assistantVersion().number());
        assertTrue(repository.findVersion(CAMPUS, version.id()).isEmpty());
    }

    @Test
    void 受控模型配置绑定必须与历史部署字段互斥持久化() {
        var assistant = assistant(STORE, "受控模型助手");
        repository.create(new AssistantCreation(
                assistant, "governed-assistant-key", "g".repeat(64)), 5);
        var advanced = assistant.advanceVersion();
        var draft = AssistantVersion.governedDraft(
                STORE,
                new AssistantVersionId(IDENTIFIERS.incrementAndGet()),
                advanced.id(),
                advanced.latestVersionNumber(),
                new GovernedChatModelBinding(CHAT, 9, 310000000000000001L, 6, 1024),
                SPACE, INDEX, 4,
                new GovernedEmbeddingModelBinding(
                        EMBEDDING, 3, 310000000000000002L, 7, 8),
                "只依据编号证据回答，并在结论后标注引用。",
                512, 5, 0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE,
                CREATOR, NOW);

        repository.createVersion(new AssistantVersionCreation(
                assistant, advanced, draft, "governed-version-key", "h".repeat(64)), 20);

        var restored = repository.findVersion(STORE, draft.id()).orElseThrow();
        assertTrue(restored.usesGovernedModelConfigurations());
        assertEquals(CHAT, restored.governedChat().configurationId());
        assertEquals(EMBEDDING, restored.governedEmbedding().configurationId());
        assertEquals(0, restored.chatDeploymentId());
        assertEquals(0, restored.embeddingDeploymentId());
        assertNull(jdbc.queryForObject(
                "SELECT chat_deployment_id FROM assistant_delivery.assistant_version WHERE tenant_id = ? AND id = ?",
                Long.class, STORE.value(), draft.id().value()));
    }

    @Test
    void 受控聊天路由应按顺序持久化并完整恢复() {
        var assistant = assistant(STORE, "后备模型助手");
        repository.create(new AssistantCreation(
                assistant, "route-assistant-key", "r".repeat(64)), 5);
        var advanced = assistant.advanceVersion();
        var route = new GovernedChatModelRoute(List.of(
                new GovernedChatModelBinding(CHAT, 9, 310000000000000001L, 6, 1024),
                new GovernedChatModelBinding(
                        320000000000000003L, 2, 310000000000000003L, 4, 768)));
        var draft = AssistantVersion.governedDraft(
                STORE,
                new AssistantVersionId(IDENTIFIERS.incrementAndGet()),
                advanced.id(),
                advanced.latestVersionNumber(),
                route,
                SPACE, INDEX, 4,
                new GovernedEmbeddingModelBinding(
                        EMBEDDING, 3, 310000000000000002L, 7, 8),
                "只依据编号证据回答，并在结论后标注引用。",
                512, 5, 0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE,
                CREATOR, NOW);

        repository.createVersion(new AssistantVersionCreation(
                assistant, advanced, draft, "route-version-key", "s".repeat(64)), 20);

        var restored = repository.findVersion(STORE, draft.id()).orElseThrow();
        assertEquals(route, restored.governedChatRoute());
        assertEquals(2, jdbc.queryForObject(
                """
                SELECT count(*)
                FROM assistant_delivery.assistant_version_chat_route_candidate
                WHERE tenant_id = ? AND assistant_version_id = ?
                """,
                Integer.class,
                STORE.value(),
                draft.id().value()));
    }

    @Test
    void 历史受控版本缺少路由子表快照时回退为原主候选() {
        var assistant = assistant(STORE, "历史受控模型助手");
        repository.create(new AssistantCreation(
                assistant, "legacy-route-assistant-key", "t".repeat(64)), 5);
        var advanced = assistant.advanceVersion();
        var draft = AssistantVersion.governedDraft(
                STORE,
                new AssistantVersionId(IDENTIFIERS.incrementAndGet()),
                advanced.id(),
                advanced.latestVersionNumber(),
                new GovernedChatModelBinding(CHAT, 9, 310000000000000001L, 6, 1024),
                SPACE, INDEX, 4,
                new GovernedEmbeddingModelBinding(
                        EMBEDDING, 3, 310000000000000002L, 7, 8),
                "只依据编号证据回答，并在结论后标注引用。",
                512, 5, 0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE,
                CREATOR, NOW);
        repository.createVersion(new AssistantVersionCreation(
                assistant, advanced, draft, "legacy-route-version-key", "u".repeat(64)), 20);
        jdbc.update(
                "DELETE FROM assistant_delivery.assistant_version_chat_route_candidate WHERE tenant_id = ? AND assistant_version_id = ?",
                STORE.value(),
                draft.id().value());

        var restored = repository.findVersion(STORE, draft.id()).orElseThrow();

        assertEquals(List.of(draft.governedChat()), restored.governedChatRoute().candidates());
    }

    @Test
    void 助手幂等请求使用独立雪花主键() {
        var assistant = assistant(STORE, "雪花主键助手");
        repository.create(
                new AssistantCreation(
                        assistant,
                        "assistant-snowflake-key",
                        "8".repeat(64)),
                5);

        var requestId = jdbc.queryForObject(
                """
                SELECT id
                FROM assistant_delivery.assistant_request
                WHERE tenant_id = ?
                  AND operation = 'CREATE_ASSISTANT'
                """,
                Long.class,
                STORE.value());

        assertTrue(requestId != null && requestId > 0L);
        assertTrue(requestId != assistant.id().value());
    }

    @Test
    void 同幂等键异参和过期聚合修订必须失败() {
        var assistant = assistant(STORE, "校园问答助手");
        repository.create(
                new AssistantCreation(assistant, "assistant-key-0002", "c".repeat(64)),
                5);
        assertThrows(
                AssistantIdempotencyConflictException.class,
                () -> repository.create(
                        new AssistantCreation(
                                assistant(STORE, "另一助手"),
                                "assistant-key-0002",
                                "d".repeat(64)),
                        5));

        var advanced = assistant.advanceVersion();
        repository.createVersion(
                new AssistantVersionCreation(
                        assistant,
                        advanced,
                        version(advanced),
                        "version-key-0002",
                        "e".repeat(64)),
                20);
        assertThrows(
                AssistantConcurrentModificationException.class,
                () -> repository.createVersion(
                        new AssistantVersionCreation(
                                assistant,
                                advanced,
                                versionWithId(advanced, IDENTIFIERS.incrementAndGet()),
                                "version-key-0003",
                                "f".repeat(64)),
                        20));
    }

    @Test
    void 发布使用乐观锁且已发布快照可恢复() {
        var assistant = assistant(STORE, "发布验收助手");
        repository.create(
                new AssistantCreation(assistant, "assistant-key-0003", "1".repeat(64)),
                5);
        var advanced = assistant.advanceVersion();
        var draft = version(advanced);
        repository.createVersion(
                new AssistantVersionCreation(
                        assistant,
                        advanced,
                        draft,
                        "version-key-0004",
                        "2".repeat(64)),
                20);

        var published = draft.publish(NOW.plusSeconds(60));
        var publication = new AssistantVersionPublication(
                published,
                0,
                "publish-key-0001",
                "3".repeat(64));
        var first = repository.publishVersion(publication);
        var replayed = repository.publishVersion(publication);
        assertFalse(first.replayed());
        assertTrue(replayed.replayed());
        var restored = repository.findVersion(STORE, draft.id()).orElseThrow();
        assertEquals("PUBLISHED", restored.status().name());
        assertEquals(9, restored.chatDeploymentRevision());
        assertEquals(4, restored.indexVersionRevision());
    }

    @Test
    void 查询目录只返回每个助手最新已发布版本并支持页码及租户隔离() {
        var first = createPublishedAssistant(
                "门店导购助手",
                "catalog-1",
                "4",
                NOW.plusSeconds(60));
        createPublishedAssistant(
                "校园问答助手",
                "catalog-2",
                "5",
                NOW.plusSeconds(90));

        var current = repository
                .findAssistant(STORE, first.assistant().id())
                .orElseThrow();
        var advanced = current.advanceVersion();
        var secondVersion = version(advanced);
        repository.createVersion(
                new AssistantVersionCreation(
                        current,
                        advanced,
                        secondVersion,
                        "catalog-version-3",
                        "6".repeat(64)),
                20);
        repository.publishVersion(new AssistantVersionPublication(
                secondVersion.publish(NOW.plusSeconds(120)),
                0,
                "catalog-publish-3",
                "7".repeat(64)));

        var firstPage = repository.findPublishedPage(STORE, 1, 1, new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", java.util.Set.of(), true));
        var secondPage = repository.findPublishedPage(STORE, 2, 1, new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", java.util.Set.of(), true));

        assertEquals(1, firstPage.items().size());
        assertEquals(secondVersion.id().value(), firstPage.items()
                .getFirst()
                .assistantVersionId());
        assertEquals(2, firstPage.items().getFirst().versionNumber());
        assertEquals(2L, firstPage.totalElements());
        assertEquals("校园问答助手", secondPage.items().getFirst().displayName());
        assertEquals(2L, secondPage.totalElements());
        assertTrue(repository.findPublishedPage(CAMPUS, 1, 20, new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", java.util.Set.of(), true))
                .items()
                .isEmpty());
    }

    @Test
    void 二百助手授权及搜索在分页和总数前过滤且空授权始终为空() {
        var ids = new java.util.HashSet<Long>();
        for (int index = 0; index < 200; index++) {
            var fixture = createPublishedAssistant("授权助手-" + String.format("%03d", index),
                    "scope-" + index, "8", NOW.plusSeconds(index));
            if (index % 50 == 0) ids.add(fixture.assistant().id().value());
        }
        var empty = repository.findPublishedPage(STORE, 1, 20,
                new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", java.util.Set.of(), false));
        assertEquals(0, empty.totalElements());
        assertTrue(empty.items().isEmpty());
        var scoped = repository.findPublishedPage(STORE, 2, 2,
                new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", ids, false));
        assertEquals(4, scoped.totalElements());
        assertEquals(2, scoped.items().size());
        assertTrue(scoped.items().stream().allMatch(item -> ids.contains(item.assistantId())));
        var filtered = repository.findPublishedPage(STORE, 1, 20,
                new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("-050", ids, false));
        assertEquals(1, filtered.totalElements());
        assertEquals("授权助手-050", filtered.items().getFirst().displayName());
        assertEquals(0, repository.findPublishedPage(CAMPUS, 1, 20,
                new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", ids, false)).totalElements());
        assertEquals(0, repository.findPublishedPage(STORE, 1, 20,
                new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("%", java.util.Set.of(), true)).totalElements());
        assertEquals(200, repository.findPublishedPage(STORE, 10, 20,
                new com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantFilter("", java.util.Set.of(), true)).totalElements());
    }

    private static Assistant assistant(TenantId tenantId, String name) {
        return Assistant.create(
                tenantId,
                new AssistantId(IDENTIFIERS.incrementAndGet()),
                name,
                CREATOR,
                NOW);
    }

    private static AssistantVersion version(Assistant advanced) {
        return versionWithId(advanced, IDENTIFIERS.incrementAndGet());
    }

    private static AssistantVersion versionWithId(Assistant advanced, long id) {
        return AssistantVersion.draft(
                STORE,
                new AssistantVersionId(id),
                advanced.id(),
                advanced.latestVersionNumber(),
                CHAT,
                9,
                1024,
                SPACE,
                INDEX,
                4,
                EMBEDDING,
                3,
                8,
                "只依据编号证据回答，并在结论后标注引用。",
                512,
                5,
                0.2,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE,
                CREATOR,
                NOW);
    }

    private PublishedFixture createPublishedAssistant(
            String name,
            String keySuffix,
            String fingerprintCharacter,
            Instant publishedAt) {
        var assistant = assistant(STORE, name);
        repository.create(
                new AssistantCreation(
                        assistant,
                        "catalog-assistant-" + keySuffix,
                        fingerprintCharacter.repeat(64)),
                200);
        var advanced = assistant.advanceVersion();
        var draft = version(advanced);
        repository.createVersion(
                new AssistantVersionCreation(
                        assistant,
                        advanced,
                        draft,
                        "catalog-version-" + keySuffix,
                        fingerprintCharacter.repeat(64)),
                20);
        repository.publishVersion(new AssistantVersionPublication(
                draft.publish(publishedAt),
                0,
                "catalog-publish-" + keySuffix,
                fingerprintCharacter.repeat(64)));
        return new PublishedFixture(assistant, draft);
    }

    private void seedReferences() {
        var provider = 310000000000000001L;
        jdbc.update(
                """
                INSERT INTO model_runtime.provider_connection (
                    tenant_id, id, provider_kind, display_name, endpoint, region,
                    authentication_type, credential_reference, status, version,
                    last_probe_succeeded, last_probe_code, last_probed_at, created_at
                ) VALUES (?, ?, 'DETERMINISTIC', '测试供应商', 'memory://deterministic',
                    '本地', 'API_KEY', 'secret://test/provider', 'VERIFIED', 1,
                    TRUE, 'DETERMINISTIC_OK', ?, ?)
                """,
                STORE.value(),
                provider,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(120)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_deployment (
                    tenant_id, id, provider_connection_id, provider_connection_revision,
                    model_code, display_name, model_type, capabilities, context_window,
                    output_limit, embedding_dimension, status, version, created_at
                ) VALUES (?, ?, ?, 1, 'deterministic-chat', '测试对话部署', 'CHAT',
                    ARRAY['CHAT'], 8192, 1024, NULL, 'DRAFT', 9, ?)
                """,
                STORE.value(),
                CHAT,
                provider,
                Timestamp.from(NOW.minusSeconds(30)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_capability_verification (
                    id, tenant_id, model_deployment_id, model_deployment_version,
                    provider_connection_id, provider_connection_revision, model_code,
                    declared_capabilities, observed_capabilities, succeeded, result_code,
                    elapsed_millis, probed_at, expires_at
                ) VALUES (?, ?, ?, 9, ?, 1, 'deterministic-chat',
                    ARRAY['CHAT'], ARRAY['CHAT'], TRUE, 'DETERMINISTIC_OK',
                    1, ?, ?)
                """,
                CHAT_VERIFICATION,
                STORE.value(),
                CHAT,
                provider,
                Timestamp.from(NOW.minusSeconds(20)),
                Timestamp.from(NOW.plusSeconds(3600)));
        jdbc.update(
                """
                UPDATE model_runtime.model_deployment
                SET status = 'PUBLISHED', last_verification_id = ?, verification_expires_at = ?
                WHERE tenant_id = ? AND id = ?
                """,
                CHAT_VERIFICATION,
                Timestamp.from(NOW.plusSeconds(3600)),
                STORE.value(),
                CHAT);
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, description, status, version, created_by, created_at
                ) VALUES (?, ?, '测试知识空间', NULL, 'DRAFT', 0, ?, ?)
                """,
                STORE.value(),
                SPACE,
                CREATOR.value(),
                Timestamp.from(NOW.minusSeconds(120)));
        jdbc.update(
                """
                INSERT INTO knowledge_data.index_version (
                    tenant_id, knowledge_space_id, id, source_snapshot_digest, chunk_policy,
                    embedding_deployment_id, embedding_deployment_revision,
                    embedding_dimension, status, version, build_digest, chunk_count,
                    failure_code, created_by, created_at, ready_at, activated_at,
                    superseded_at
                ) VALUES (?, ?, ?, ?, 'fixed-800-120', ?, 3, 8, 'ACTIVE', 4, ?,
                    2, NULL, ?, ?, ?, ?, NULL)
                """,
                STORE.value(),
                SPACE,
                INDEX,
                "a".repeat(64),
                EMBEDDING,
                "b".repeat(64),
                CREATOR.value(),
                Timestamp.from(NOW.minusSeconds(90)),
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(30)));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper")
    static class TestApplication {

        @Bean
        AssistantRepository assistantRepository(
                AssistantMapper mapper,
                AssistantRequestMapper requestMapper,
                AssistantVersionMapper versionMapper,
                AssistantVersionChatRouteCandidateMapper routeCandidateMapper,
                EntityIdGenerator identifiers,
                PlatformTransactionManager transactions) {
            return new PostgresAssistantRepository(
                    mapper,
                    requestMapper,
                    versionMapper,
                    routeCandidateMapper,
                    identifiers,
                    transactions);
        }
    }

    private record PublishedFixture(
            Assistant assistant,
            AssistantVersion version) {
    }
}
