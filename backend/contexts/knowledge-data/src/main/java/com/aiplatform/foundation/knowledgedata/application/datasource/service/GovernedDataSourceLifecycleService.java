package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleAction;
import com.aiplatform.foundation.knowledgedata.application.datasource.command.GovernedDataSourceLifecycleCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.util.Objects;

/**
 * 在可信权限和乐观修订边界内编排受治理来源的短状态动作。
 *
 * <p>该服务不执行外部连接探测、文件读取或嵌入调用；长耗时工作必须在事务外完成，再通过受控结果路径登记。</p>
 */
public final class GovernedDataSourceLifecycleService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final GovernedDataSourceRepository repository;

    public GovernedDataSourceLifecycleService(GovernedDataSourceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
    }

    /**
     * 执行来源状态迁移。
     *
     * @throws KnowledgePermissionDeniedException 主体缺少知识写权限时抛出。
     * @throws DataSourceNotFoundException 来源不属于当前租户与知识空间时抛出。
     * @throws DataSourceConcurrentModificationException 预期修订已过期时抛出。
     */
    public GovernedDataSourceLifecycleResult transition(
            GovernedDataSourceLifecycleCommand command) {
        Objects.requireNonNull(command, "来源生命周期命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var knowledgeSpaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var dataSourceId = DataSourceId.parse(command.dataSourceId());
        var source = repository.findSource(
                        command.context().tenantId(),
                        knowledgeSpaceId,
                        dataSourceId)
                .orElseThrow(DataSourceNotFoundException::new);
        if (source.version() != command.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }
        var changed = apply(source, command.action(), command.expectedVersion());
        if (!repository.update(changed, command.expectedVersion())) {
            throw new DataSourceConcurrentModificationException();
        }
        return GovernedDataSourceLifecycleResult.from(changed);
    }

    private static GovernedDataSource apply(
            GovernedDataSource source,
            GovernedDataSourceLifecycleAction action,
            long expectedVersion) {
        return switch (action) {
            case START_PROBE -> source.startProbe(expectedVersion);
            case COMPLETE_PROBE -> source.completeProbe(expectedVersion);
            case FAIL_PROBE -> source.failProbe(expectedVersion);
            case START_SYNCHRONIZATION -> source.startSynchronization(expectedVersion);
            case FAIL_SYNCHRONIZATION -> source.failSynchronization(expectedVersion);
            case RETRY -> source.retry(expectedVersion);
            case RETIRE -> source.retire(expectedVersion);
        };
    }
}
