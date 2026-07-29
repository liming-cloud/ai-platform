package com.aiplatform.foundation.knowledgedata.application.datasource.command;

/**
 * 知识管理员或受控工作进程可发起的来源生命周期动作。
 */
public enum GovernedDataSourceLifecycleAction {
    START_PROBE,
    COMPLETE_PROBE,
    FAIL_PROBE,
    START_SYNCHRONIZATION,
    FAIL_SYNCHRONIZATION,
    RETRY,
    RETIRE
}
