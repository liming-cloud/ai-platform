package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterStructuredDatasetCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStoragePort;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvStorageRequest;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/** 将平台保存的私有 UTF-8 CSV 及字段映射登记为不可变 STRUCTURED_DATASET 修订。 */
public final class GovernedStructuredDatasetRegistrationService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private final GovernedDataSourceRepository repository;
    private final TextContentStagingPort staging;
    private final StructuredDatasetCsvStoragePort storage;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public GovernedStructuredDatasetRegistrationService(
            GovernedDataSourceRepository repository,
            TextContentStagingPort staging,
            StructuredDatasetCsvStoragePort storage,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.staging = Objects.requireNonNull(staging, "UTF-8 暂存端口不能为空");
        this.storage = Objects.requireNonNull(storage, "私有 CSV 存储端口不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 鉴权后暂存并保存 CSV，再原子登记来源与首个冻结修订。
     *
     * @throws KnowledgePermissionDeniedException 当前成员没有 `knowledge:write` 时抛出。
     */
    public GovernedDataSourceLifecycleResult register(RegisterStructuredDatasetCommand command) {
        Objects.requireNonNull(command, "结构化数据集登记命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var space = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var now = clock.instant();
        var source = GovernedDataSource.create(
                command.context().tenantId(),
                space,
                new DataSourceId(identifiers.nextId()),
                GovernedDataSourceType.STRUCTURED_DATASET,
                command.name(),
                new CreatorId(command.context().membershipId().value()),
                now);
        try (var staged = staging.stage(command.content())) {
            var csv = storage.put(new StructuredDatasetCsvStorageRequest(
                    source.tenantId(), space, source.id(), source.currentRevision(), staged, now));
            var definition = new StructuredDatasetCsvDefinition(
                    csv,
                    command.mappingVersion(),
                    command.contentField(),
                    command.titleField(),
                    command.fields(),
                    command.rowFilter());
            try {
                repository.register(source, SourceRevision.structuredDataset(
                        source, definition, source.createdBy(), now));
            } catch (RuntimeException exception) {
                storage.delete(csv);
                throw exception;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("CSV 暂存失败", exception);
        }
        return GovernedDataSourceLifecycleResult.from(source);
    }
}
