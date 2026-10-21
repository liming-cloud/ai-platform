package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.publication.PlatformSupplyFacts;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.publication.PlatformModelSupplyPublicationStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformCapabilitySafetySuite;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelProbeProviderService;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformCapabilityVerificationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformSupplyPublicationMapper;
import com.aiplatform.foundation.security.identity.PlatformModelWriteFacts;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TenantActivityFacts;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 当前事实、完整验证、目录、精确租户授权、CAS 与独立审计仅在同一短事务提交。 */
public final class PostgresPlatformModelSupplyPublicationStore implements PlatformModelSupplyPublicationStore {
    private final PlatformSupplyPublicationMapper mapper;
    private final PlatformCapabilityVerificationMapper verification;
    private final PlatformModelDeploymentMapper locks;
    private final PlatformModelDeploymentRepository deployments;
    private final PlatformProviderConnectionRepository connections;
    private final PlatformProviderModelDiscoveryLookup discoveries;
    private final ModelConfigurationRepository catalog;
    private final Supplier<PlatformModelWriteFacts> actors;
    private final Supplier<TenantActivityFacts> tenants;
    private final Supplier<com.aiplatform.foundation.security.identity.TenantFeatureAvailability> features;
    private final EntityIdGenerator identifiers;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PostgresPlatformModelSupplyPublicationStore(PlatformSupplyPublicationMapper mapper,
            PlatformCapabilityVerificationMapper verification, PlatformModelDeploymentMapper locks,
            PlatformModelDeploymentRepository deployments, PlatformProviderConnectionRepository connections,
            PlatformProviderModelDiscoveryLookup discoveries, ModelConfigurationRepository catalog,
            Supplier<PlatformModelWriteFacts> actors, Supplier<TenantActivityFacts> tenants,
            Supplier<com.aiplatform.foundation.security.identity.TenantFeatureAvailability> features,
            EntityIdGenerator identifiers, Clock clock, PlatformTransactionManager manager) {
        this.mapper = Objects.requireNonNull(mapper); this.verification = Objects.requireNonNull(verification);
        this.locks = Objects.requireNonNull(locks); this.deployments = Objects.requireNonNull(deployments);
        this.connections = Objects.requireNonNull(connections); this.discoveries = Objects.requireNonNull(discoveries);
        this.catalog = Objects.requireNonNull(catalog); this.actors = Objects.requireNonNull(actors);
        this.tenants = Objects.requireNonNull(tenants); this.identifiers = Objects.requireNonNull(identifiers);
        this.features = Objects.requireNonNull(features);
        this.clock = Objects.requireNonNull(clock); transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        transactions.setTimeout(3);
    }

    @Override
    public PlatformModelDeployment publish(PlatformRequestContext actor, PlatformModelDeployment expected, BooleanSupplier session) {
        return Objects.requireNonNull(transactions.execute(status -> {
            requireActor(actor, session);
            var now = clock.instant();
            requireSource(expected, now);
            var current = lock(expected.id(), expected.version());
            if (current.inputs() == null || current.status() != PlatformModelDeploymentStatus.VERIFIED
                    || !Objects.equals(current.inputs(), expected.inputs())
                    || !PlatformModelProbeProviderService.verificationTargetFingerprint(current, current.version()).equals(
                            PlatformModelProbeProviderService.verificationTargetFingerprint(expected, expected.version())))
                throw new IllegalStateException("没有完整当前验证或声明已变化");
            var inputs = PlatformModelProbeProviderService.verificationInputsDigest(current);
            if (verificationId(current, inputs, now) == null) throw new IllegalStateException("没有绑定当前输入的完整三样本验证与审计");
            Objects.requireNonNull(tenants.get(), "活动租户事实不可用").requireActive(current.inputs().plannedTenantIds());
            var featureOwner = Objects.requireNonNull(features.get(), "当前租户功能事实不可用");
            for (long tenantId : current.inputs().plannedTenantIds().stream().sorted().toList())
                featureOwner.requireAvailable(new com.aiplatform.foundation.shared.kernel.tenant.TenantId(tenantId), "modelGovernance");
            var connection = connections.findById(current.providerConnectionId()).orElseThrow();
            if (!connection.region().equals(current.inputs().residency())) throw new IllegalStateException("冻结驻留与当前来源不一致");
            var published = current.publish(now);
            var capability = switch (published.modelType()) {
                case CHAT -> ModelRuntimeCapabilities.chat(published.contextWindow(), published.outputLimit());
                case EMBEDDING -> ModelRuntimeCapabilities.embedding(published.contextWindow(), published.embeddingDimension());
                case RERANK -> ModelRuntimeCapabilities.rerank(published.contextWindow(), published.inputs().rerankCandidateLimit());
            };
            var entry = ControlledModelCatalogEntry.createFromPlatformDeployment(new ControlledModelCatalogEntryId(identifiers.nextId()),
                    published.id(), published.version(), connection.providerKind(), connection.endpoint(), published.modelCode(),
                    published.modelType(), capability, now).publish();
            catalog.saveCatalogEntry(entry);
            var publicationId = identifiers.nextId();
            if (mapper.publish(publicationId, published, entry.id().value(), actor, inputs, now) != 1
                    || mapper.grant(publicationId, current.inputs().plannedTenantIds().stream().sorted().map(Object::toString)
                            .collect(Collectors.joining(",")), now) != current.inputs().plannedTenantIds().size()
                    || !deployments.update(published, current.version())) throw new IllegalStateException("发布或完整租户集合未提交");
            audit(actor, published.id(), publicationId, null, "PUBLISH");
            live(session);
            var committingAt = clock.instant();
            requireSource(current, committingAt);
            if (!current.verificationExpiresAt().isAfter(committingAt)) throw new IllegalStateException("提交时验证已过期");
            return published;
        }));
    }

    private void requireSource(PlatformModelDeployment d, java.time.Instant now) {
        if (d.inputs() == null || locks.lockSource(d.providerConnectionId().value(), d.providerConnectionRevision(), now) == null)
            throw new IllegalStateException("发布来源或完整声明已失效");
        var source = discoveries.findCurrentSnapshot(d.providerConnectionId(), d.providerConnectionRevision(),
                d.modelCode(), d.modelType(), now).orElseThrow(() -> new IllegalStateException("发布候选不可用"));
        if (source.discoveryId() != d.inputs().discoveryId() || !source.candidate().capabilities().equals(d.capabilities()))
            throw new IllegalStateException("发现根或能力已变化");
    }

    private Long verificationId(PlatformModelDeployment d, String inputs, java.time.Instant now) {
        if (d.lastVerificationId() == null || d.version() < 1) return null;
        return mapper.completeVerification(d, inputs, PlatformModelProbeProviderService.verificationTargetFingerprint(d, d.version() - 1),
                PlatformCapabilitySafetySuite.VERSION, String.join(",", PlatformCapabilitySafetySuite.sampleDigests(d.modelType())), now);
    }

    @Override
    public void revoke(PlatformRequestContext actor, PlatformModelDeploymentId id, long tenantId,
            long deploymentVersion, long grantVersion, BooleanSupplier session) {
        if (tenantId <= 0 || grantVersion < 0) throw new IllegalArgumentException("撤权目标或修订无效");
        transactions.executeWithoutResult(status -> {
            requireActor(actor, session);
            lock(id, deploymentVersion);
            var fact = facts(id).orElseThrow(() -> new IllegalStateException("部署没有独立供应发布事实"));
            long publicationId = Long.parseLong(fact.publicationId());
            if (!Objects.equals(mapper.lockGrant(publicationId, tenantId), grantVersion)
                    || mapper.revoke(publicationId, tenantId, grantVersion, clock.instant()) != 1)
                throw new IllegalStateException("供应授权不存在、已撤销或修订已变化");
            audit(actor, id, publicationId, tenantId, "REVOKE");
            live(session);
        });
    }

    @Override
    public PlatformModelDeployment disable(PlatformRequestContext actor, PlatformModelDeployment expected, BooleanSupplier session) {
        return Objects.requireNonNull(transactions.execute(status -> {
            requireActor(actor, session);
            var current = lock(expected.id(), expected.version());
            var disabled = current.disable();
            if (disabled != current) {
                if (!deployments.update(disabled, current.version())) throw new IllegalStateException("平台部署修订已变化");
                audit(actor, current.id(), facts(current.id()).map(f -> Long.valueOf(f.publicationId())).orElse(null), null, "DISABLE");
            }
            live(session);
            return disabled;
        }));
    }

    @Override
    public Optional<PlatformSupplyFacts> facts(PlatformModelDeploymentId id) {
        return Optional.ofNullable(mapper.facts(Objects.requireNonNull(id).value())).map(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformSupplyFactsRow::view);
    }

    private PlatformModelDeployment lock(PlatformModelDeploymentId id, long version) {
        if (verification.lockDeployment(id.value()) == null) throw new IllegalStateException("平台部署不存在");
        var current = deployments.findById(id).orElseThrow();
        if (current.version() != version) throw new IllegalStateException("平台部署修订已变化");
        return current;
    }

    private void requireActor(PlatformRequestContext actor, BooleanSupplier session) {
        live(session);
        Objects.requireNonNull(actors.get(), "当前平台权限事实不可用").requireCurrent(actor);
    }

    private static void live(BooleanSupplier session) {
        if (Thread.currentThread().isInterrupted() || !Objects.requireNonNull(session).getAsBoolean())
            throw new InvalidAuthenticatedSessionException();
    }

    private void audit(PlatformRequestContext actor, PlatformModelDeploymentId id, Long publication, Long tenant, String action) {
        if (mapper.audit(identifiers.nextId(), id.value(), publication, tenant, actor, action, clock.instant()) != 1)
            throw new IllegalStateException("供应审计未保存");
    }
}
