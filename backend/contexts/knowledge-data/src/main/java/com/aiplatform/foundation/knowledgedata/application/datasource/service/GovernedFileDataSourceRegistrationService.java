package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.FileSourceObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/** 将平台保存的 UTF-8 文件登记为不可变 FILE 修订。 */
public final class GovernedFileDataSourceRegistrationService {
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private final GovernedDataSourceRepository repository;
    private final TextContentStagingPort staging;
    private final TextContentObjectStoragePort storage;
    private final EntityIdGenerator identifiers;
    private final Clock clock;
    public GovernedFileDataSourceRegistrationService(GovernedDataSourceRepository repository,
            TextContentStagingPort staging, TextContentObjectStoragePort storage,
            EntityIdGenerator identifiers, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.staging = Objects.requireNonNull(staging, "文本暂存端口不能为空");
        this.storage = Objects.requireNonNull(storage, "对象存储端口不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }
    public GovernedDataSourceLifecycleResult register(RegisterFileDataSourceCommand command) {
        Objects.requireNonNull(command, "文件登记命令不能为空");
        if (!command.context().hasPermission(WRITE)) throw new KnowledgePermissionDeniedException();
        var space = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var now = clock.instant();
        var source = GovernedDataSource.create(command.context().tenantId(), space,
                new DataSourceId(identifiers.nextId()), GovernedDataSourceType.FILE, command.name(),
                new CreatorId(command.context().membershipId().value()), now);
        try (var staged = staging.stage(command.content())) {
            var stored = storage.put(new TextContentStorageRequest(source.tenantId(), space, source.id(), 1, staged, now));
            var file = new FileSourceObjectReference(stored.objectKey(), stored.sha256(), stored.byteSize(), "text/plain; charset=utf-8");
            try { repository.register(source, SourceRevision.file(source, file, source.createdBy(), now)); }
            catch (RuntimeException exception) { storage.delete(stored); throw exception; }
        } catch (IOException exception) { throw new IllegalStateException("文件暂存失败", exception); }
        return GovernedDataSourceLifecycleResult.from(source);
    }
}
