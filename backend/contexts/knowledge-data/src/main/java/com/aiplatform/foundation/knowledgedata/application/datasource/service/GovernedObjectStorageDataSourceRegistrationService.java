package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.RegisterObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeSelectionPort;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;

/** 将平台已核验的对象范围选择登记为不可变 OBJECT_STORAGE 修订。 */
public final class GovernedObjectStorageDataSourceRegistrationService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private final GovernedDataSourceRepository repository;
    private final PlatformObjectRangeSelectionPort selections;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public GovernedObjectStorageDataSourceRegistrationService(
            GovernedDataSourceRepository repository,
            PlatformObjectRangeSelectionPort selections,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.selections = Objects.requireNonNull(selections, "平台对象范围解析端口不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 鉴权后解析平台范围并原子登记来源及其首个不可变修订。
     *
     * @throws KnowledgePermissionDeniedException 当前成员没有 `knowledge:write` 时抛出。
     */
    public GovernedDataSourceLifecycleResult register(RegisterObjectStorageDataSourceCommand command) {
        Objects.requireNonNull(command, "对象存储来源登记命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var space = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var rangeId = new PlatformObjectRangeId(command.rangeId());
        var collection = selections.resolve(
                command.context().tenantId(), space, rangeId, command.selectionRevision());
        if (!collection.rangeId().equals(rangeId) || collection.selectionRevision() != command.selectionRevision()) {
            throw new IllegalStateException("平台对象范围解析结果与请求不一致");
        }
        var now = clock.instant();
        var source = GovernedDataSource.create(
                command.context().tenantId(),
                space,
                new DataSourceId(identifiers.nextId()),
                GovernedDataSourceType.OBJECT_STORAGE,
                command.name(),
                new CreatorId(command.context().membershipId().value()),
                now);
        repository.register(source, SourceRevision.objectStorage(source, collection, source.createdBy(), now));
        return GovernedDataSourceLifecycleResult.from(source);
    }
}
