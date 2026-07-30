package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRun;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 草稿测试运行的独立配额占用、终态提交与租户隔离读取端口。
 */
public interface AssistantTestRunRepository {

    /**
     * 原子占用独立草稿测试配额并保存运行起点。
     */
    AssistantTestRun claim(AssistantTestRun proposed, AssistantTestQuotaPolicy quota);

    /**
     * 以乐观版本提交已经完成的草稿测试运行。
     */
    void complete(AssistantTestRun run, long expectedVersion);

    /**
     * 只在租户边界内读取草稿测试运行。
     */
    Optional<AssistantTestRun> find(TenantId tenantId, AssistantTestRunId runId);
}
