package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectRangeSelectionPort;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.util.Objects;

/** 编排对象存储冻结范围的事务外一致性探测与失败恢复。 */
public final class ObjectStorageDataSourceProbeService {
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private final GovernedDataSourceRepository repository; private final PlatformObjectRangeSelectionPort selections;
    public ObjectStorageDataSourceProbeService(GovernedDataSourceRepository repository, PlatformObjectRangeSelectionPort selections) { this.repository=Objects.requireNonNull(repository,"受治理数据源仓储不能为空"); this.selections=Objects.requireNonNull(selections,"平台对象范围解析端口不能为空"); }
    public GovernedDataSourceLifecycleResult probe(ProbeObjectStorageDataSourceCommand command) {
        Objects.requireNonNull(command,"对象存储探测命令不能为空"); if(!command.context().hasPermission(WRITE)) throw new KnowledgePermissionDeniedException();
        var space=KnowledgeSpaceId.parse(command.knowledgeSpaceId()); var id=DataSourceId.parse(command.dataSourceId());
        var source=repository.findSource(command.context().tenantId(),space,id).orElseThrow(DataSourceNotFoundException::new);
        if(source.type()!=GovernedDataSourceType.OBJECT_STORAGE) throw new DataSourceNotFoundException();
        if(source.version()!=command.expectedVersion()) throw new DataSourceConcurrentModificationException();
        var probing=start(source,command.expectedVersion()); if(!repository.update(probing,command.expectedVersion())) throw new DataSourceConcurrentModificationException();
        try { var revision=repository.findRevision(command.context().tenantId(),space,id,source.currentRevision()).orElseThrow(DataSourceNotFoundException::new); var frozen=revision.objectStorageObjects(); var resolved=selections.resolve(command.context().tenantId(),space,frozen.rangeId(),frozen.selectionRevision()); if(!frozen.equals(resolved)) throw new IllegalStateException("平台对象范围选择已变化"); }
        catch(RuntimeException exception) { var failed=probing.failProbe(probing.version()); update(failed,probing.version()); return GovernedDataSourceLifecycleResult.from(failed); }
        var ready=probing.completeProbe(probing.version()); update(ready,probing.version()); return GovernedDataSourceLifecycleResult.from(ready);
    }
    private void update(GovernedDataSource source,long expected){ if(!repository.update(source,expected)) throw new DataSourceConcurrentModificationException(); }
    private static GovernedDataSource start(GovernedDataSource source,long expected){ if(source.status()!=GovernedDataSourceStatus.FAILED) return source.startProbe(expected); if(source.failedOperation()!=DataSourceOperation.PROBE) throw new DataSourceStateException("当前失败操作不允许重新探测"); return source.retry(expected); }
}
